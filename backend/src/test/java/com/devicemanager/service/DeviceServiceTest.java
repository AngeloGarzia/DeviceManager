package com.devicemanager.service;

import com.devicemanager.dto.DeviceRequest;
import com.devicemanager.dto.DeviceResponse;
import com.devicemanager.entity.Device;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    private static final Logger log = LoggerFactory.getLogger(DeviceServiceTest.class);

    @Mock private DeviceRepository deviceRepository;
    @Mock private SfmService sfmService;
    @Mock private MasService masService;
    @Mock private StorageService storageService;
    @Mock private ImageOptimizationService imageOptimizationService;
    @Mock private AtelierService atelierService;
    @InjectMocks private DeviceService deviceService;

    private MockMultipartFile photo;

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
        photo = new MockMultipartFile("photo", "pic.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});
    }

    private DeviceRequest request() {
        DeviceRequest request = new DeviceRequest();
        request.setNom(" Carte mère ");
        request.setReference(" REF-100 ");
        request.setUsage(" Remplacement ");
        request.setDateAcquisition(LocalDate.of(2024, 1, 10));
        request.setObsolete(false);
        request.setSfmId(30L);
        request.setMasId(20L);
        return request;
    }

    @Test
    void create_storesDeviceAndPhoto() {
        log.info("Test create device");
        when(deviceRepository.existsByNomIgnoreCaseAndAtelierId("Carte mère", 100L)).thenReturn(false);
        when(deviceRepository.existsByReferenceIgnoreCaseAndAtelierId("REF-100", 100L)).thenReturn(false);
        when(sfmService.getEntity(30L)).thenReturn(TestFixtures.sfm());
        when(masService.getEntity(20L)).thenReturn(TestFixtures.mas());
        when(imageOptimizationService.optimize(photo)).thenReturn(photo);
        when(storageService.store(photo)).thenReturn(new StorageService.StoredObject("k1", "/u/k1", "image/jpeg", 3L));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            d.setId(41L);
            return d;
        });

        DeviceResponse response = deviceService.create(request(), photo);

        assertThat(response.getNom()).isEqualTo("Carte mère");
        assertThat(response.getReference()).isEqualTo("REF-100");
        assertThat(response.getPhotoUrl()).isEqualTo("/u/k1");
        verify(imageOptimizationService).optimize(photo);
        verify(storageService).store(photo);
    }

    @Test
    void create_requiresPhoto() {
        assertThatThrownBy(() -> deviceService.create(request(), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("La photo est obligatoire");
    }

    @Test
    void create_rejectsNonImage() {
        MockMultipartFile bad = new MockMultipartFile("photo", "a.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> deviceService.create(request(), bad))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Le fichier doit être une image");
    }

    @Test
    void create_rejectsDuplicateNom() {
        when(deviceRepository.existsByNomIgnoreCaseAndAtelierId("Carte mère", 100L)).thenReturn(true);

        assertThatThrownBy(() -> deviceService.create(request(), photo))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).isEqualTo("Nom de pièce déjà utilisé dans cet atelier");
                });
    }

    @Test
    void create_rejectsDuplicateReference() {
        when(deviceRepository.existsByNomIgnoreCaseAndAtelierId("Carte mère", 100L)).thenReturn(false);
        when(deviceRepository.existsByReferenceIgnoreCaseAndAtelierId("REF-100", 100L)).thenReturn(true);

        assertThatThrownBy(() -> deviceService.create(request(), photo))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Référence déjà utilisée dans cet atelier");
    }

    @Test
    void delete_removesPhotoAndEntity() {
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(TestFixtures.device()));

        deviceService.delete(40L);

        verify(storageService).delete("key");
        verify(deviceRepository).delete(any(Device.class));
    }

    @Test
    void findAll_listsDevices() {
        when(deviceRepository.findAllWithRelations(100L)).thenReturn(List.of(TestFixtures.device()));

        assertThat(deviceService.findAll("")).hasSize(1);
    }
}

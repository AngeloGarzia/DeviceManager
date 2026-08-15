package com.devicemanager.service;

import com.devicemanager.dto.DeviceRequest;
import com.devicemanager.dto.DeviceResponse;
import com.devicemanager.entity.Device;
import com.devicemanager.repository.DeviceRepository;
import com.devicemanager.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock private StockMouvementService stockMouvementService;
    @Mock private UserRepository userRepository;
    @InjectMocks private DeviceService deviceService;

    private MockMultipartFile photo;

    @BeforeEach
    void setUp() {
        lenient().when(atelierService.requireCurrentAtelier()).thenReturn(TestFixtures.atelier());
        photo = new MockMultipartFile("photos", "pic.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});
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

        DeviceResponse response = deviceService.create(request(), List.of(photo), List.of());

        assertThat(response.getNom()).isEqualTo("Carte mère");
        assertThat(response.getReference()).isEqualTo("REF-100");
        assertThat(response.getPhotoUrl()).isEqualTo("/u/k1");
        assertThat(response.getPhotos()).hasSize(1);
        verify(imageOptimizationService).optimize(photo);
        verify(storageService).store(photo);
    }

    @Test
    void create_allowsMissingReferenceSfmAndMas() {
        DeviceRequest req = request();
        req.setReference(null);
        req.setSfmId(null);
        req.setMasId(null);
        when(deviceRepository.existsByNomIgnoreCaseAndAtelierId("Carte mère", 100L)).thenReturn(false);
        when(imageOptimizationService.optimize(photo)).thenReturn(photo);
        when(storageService.store(photo)).thenReturn(new StorageService.StoredObject("k1", "/u/k1", "image/jpeg", 3L));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            d.setId(42L);
            return d;
        });

        DeviceResponse response = deviceService.create(req, List.of(photo), List.of());

        assertThat(response.getReference()).isNull();
        assertThat(response.getSfmId()).isNull();
        assertThat(response.getMasId()).isNull();
        assertThat(response.getMarqueId()).isNull();
        verify(sfmService, never()).getEntity(any());
        verify(masService, never()).getEntity(any());
    }

    @Test
    void create_requiresPhoto() {
        assertThatThrownBy(() -> deviceService.create(request(), List.of(), List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Ajoutez au moins une photo de la pièce");
    }

    @Test
    void create_rejectsMoreThanFivePhotos() {
        MockMultipartFile p2 = new MockMultipartFile("photos", "2.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{2});
        MockMultipartFile p3 = new MockMultipartFile("photos", "3.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{3});
        MockMultipartFile p4 = new MockMultipartFile("photos", "4.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{4});
        MockMultipartFile p5 = new MockMultipartFile("photos", "5.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{5});
        MockMultipartFile p6 = new MockMultipartFile("photos", "6.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{6});

        assertThatThrownBy(() -> deviceService.create(request(), List.of(photo, p2, p3, p4, p5, p6), List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Maximum 5 photos par pièce détachée");
    }

    @Test
    void create_rejectsNonImage() {
        MockMultipartFile bad = new MockMultipartFile("photos", "a.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> deviceService.create(request(), List.of(bad), List.of()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getReason())
                .isEqualTo("Le fichier doit être une photo (JPEG, PNG…)");
    }

    @Test
    void create_rejectsDuplicateNom() {
        when(deviceRepository.existsByNomIgnoreCaseAndAtelierId("Carte mère", 100L)).thenReturn(true);

        assertThatThrownBy(() -> deviceService.create(request(), List.of(photo), List.of()))
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

        assertThatThrownBy(() -> deviceService.create(request(), List.of(photo), List.of()))
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

    @Test
    void findAll_searchesByQuery() {
        when(deviceRepository.search(100L, "Carte")).thenReturn(List.of(TestFixtures.device()));

        assertThat(deviceService.findAll("Carte")).hasSize(1);
        verify(deviceRepository).search(100L, "Carte");
        verify(deviceRepository, never()).findAllWithRelations(any());
    }

    @Test
    void findAll_searchesByUsage() {
        Device device = TestFixtures.device();
        device.setUsage("Remplacement écran tactile");
        when(deviceRepository.search(100L, "tactile")).thenReturn(List.of(device));

        var results = deviceService.findAll("tactile");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getUsage()).containsIgnoringCase("tactile");
        verify(deviceRepository).search(100L, "tactile");
    }

    @Test
    void findAll_filtersMultiTokenAcrossFields() {
        Device match = TestFixtures.device(); // nom Carte + marque Novomatic
        Device other = TestFixtures.device();
        other.setId(41L);
        other.setNom("Alimentation");
        other.setMarque(TestFixtures.marque());

        when(deviceRepository.search(100L, "Carte")).thenReturn(List.of(match, other));

        var results = deviceService.findAll("Carte Novomatic");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getNom()).isEqualTo("Carte mère");
    }

    @Test
    void findAll_multiTokenExcludesPartialMatches() {
        when(deviceRepository.search(100L, "Carte")).thenReturn(List.of(TestFixtures.device()));

        assertThat(deviceService.findAll("Carte Inexistant")).isEmpty();
    }

    @Test
    void findById_returnsDevice() {
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(TestFixtures.device()));

        DeviceResponse response = deviceService.findById(40L);

        assertThat(response.getId()).isEqualTo(40L);
        assertThat(response.getReference()).isEqualTo("REF-001");
    }

    @Test
    void findById_notFound() {
        when(deviceRepository.findByIdWithRelations(99L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceService.findById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateStock_recordsManualMouvementWhenChanged() {
        Device device = TestFixtures.device();
        device.setStock(4);
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(TestFixtures.user("admin", "ADMIN")));
        when(stockMouvementService.record(any(), any(), anyInt(), anyInt(), anyString(), any(), anyString()))
                .thenAnswer(inv -> null);

        DeviceResponse response = deviceService.updateStock(40L, 7, "admin");

        assertThat(response.getStock()).isEqualTo(7);
        verify(stockMouvementService).record(
                any(),
                eq(device),
                eq(4),
                eq(7),
                eq("MANUAL"),
                eq(40L),
                anyString());
    }

    @Test
    void updateStock_skipsMouvementWhenUnchanged() {
        Device device = TestFixtures.device();
        device.setStock(4);
        when(deviceRepository.findByIdWithRelations(40L, 100L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> inv.getArgument(0));

        deviceService.updateStock(40L, 4, "admin");

        verify(stockMouvementService, never()).record(any(), any(), anyInt(), anyInt(), anyString(), any(), anyString());
    }
}

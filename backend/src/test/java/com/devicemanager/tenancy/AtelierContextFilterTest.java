package com.devicemanager.tenancy;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Casino;
import com.devicemanager.entity.Groupe;
import com.devicemanager.repository.AtelierRepository;
import com.devicemanager.repository.UserRepository;
import com.devicemanager.security.Roles;
import com.devicemanager.support.TestFixtures;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtelierContextFilterTest {

    @Mock private AtelierRepository atelierRepository;
    @Mock private UserRepository userRepository;
    @Mock private FilterChain filterChain;
    @InjectMocks private AtelierContextFilter filter;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        AtelierContext.clear();
    }

    @Test
    void allowsAtelierOfSameGroupe() throws Exception {
        authenticate("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(TestFixtures.user("admin", Roles.ADMIN)));
        when(atelierRepository.findByIdWithCasino(100L)).thenReturn(Optional.of(TestFixtures.atelier()));

        MockHttpServletRequest request = request("/api/devices", "100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void allowsTechnicienOnPreferredAtelier() throws Exception {
        authenticate("tech");
        var tech = TestFixtures.user("tech", Roles.TECHNICIEN);
        tech.setPreferredAtelier(TestFixtures.atelier());
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        when(atelierRepository.findByIdWithCasino(100L)).thenReturn(Optional.of(TestFixtures.atelier()));

        MockHttpServletRequest request = request("/api/devices", "100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsTechnicienOnOtherAtelier() throws Exception {
        authenticate("tech");
        var tech = TestFixtures.user("tech", Roles.TECHNICIEN);
        tech.setPreferredAtelier(TestFixtures.atelier());
        when(userRepository.findByUsername("tech")).thenReturn(Optional.of(tech));
        Groupe same = TestFixtures.groupe();
        Casino casino = Casino.builder().id(11L).nom("X").groupe(same).build();
        Atelier other = Atelier.builder().id(200L).nom("Autre").casino(casino).build();
        when(atelierRepository.findByIdWithCasino(200L)).thenReturn(Optional.of(other));

        MockHttpServletRequest request = request("/api/devices", "200");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("atelier préféré");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsAtelierOfOtherGroupe() throws Exception {
        authenticate("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(TestFixtures.user("admin", Roles.ADMIN)));
        Groupe other = Groupe.builder().id(2L).nom("Autre").build();
        Casino casino = Casino.builder().id(11L).nom("X").groupe(other).build();
        Atelier foreign = Atelier.builder().id(999L).nom("Foreign").casino(casino).build();
        when(atelierRepository.findByIdWithCasino(999L)).thenReturn(Optional.of(foreign));

        MockHttpServletRequest request = request("/api/devices", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Atelier non autorisé");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsInvalidAtelierId() throws Exception {
        authenticate("admin");

        MockHttpServletRequest request = request("/api/devices", "abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("Atelier sélectionné invalide");
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void skipsAuthPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userRepository, atelierRepository);
    }

    @Test
    void continuesWithoutHeader() throws Exception {
        authenticate("admin");
        MockHttpServletRequest request = request("/api/devices", null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userRepository, atelierRepository);
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private MockHttpServletRequest request(String path, String atelierId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (atelierId != null) {
            request.addHeader(AtelierContextFilter.HEADER, atelierId);
        }
        return request;
    }
}

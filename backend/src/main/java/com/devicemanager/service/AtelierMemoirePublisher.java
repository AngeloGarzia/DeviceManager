package com.devicemanager.service;

import com.devicemanager.event.AtelierSituationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Publication d'événements vers la mémoire synaptique de l'atelier courant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AtelierMemoirePublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final AtelierService atelierService;

    public void publish(String type, String fact) {
        try {
            Long atelierId = atelierService.requireCurrentAtelier().getId();
            eventPublisher.publishEvent(new AtelierSituationEvent(atelierId, type, fact));
        } catch (Exception ex) {
            log.debug("Publication mémoire synaptique ignorée: {}", ex.getMessage());
        }
    }
}

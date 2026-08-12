package com.devicemanager.service;

import com.devicemanager.entity.Atelier;
import com.devicemanager.entity.Device;
import com.devicemanager.entity.StockMouvement;
import com.devicemanager.repository.StockMouvementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Enregistrement des mouvements de stock pour la timeline.
 */
@Service
@RequiredArgsConstructor
public class StockMouvementService {

    private final StockMouvementRepository stockMouvementRepository;

    /**
     * Archive un mouvement de stock.
     */
    public StockMouvement record(
            Atelier atelier,
            Device device,
            int stockAvant,
            int stockApres,
            String sourceType,
            Long sourceId,
            String acteurNom) {
        int delta = stockApres - stockAvant;
        StockMouvement mouvement = StockMouvement.builder()
                .atelier(atelier)
                .device(device)
                .pieceNom(device.getNom())
                .pieceReference(device.getReference())
                .delta(delta)
                .stockAvant(stockAvant)
                .stockApres(stockApres)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .acteurNom(acteurNom)
                .build();
        return stockMouvementRepository.save(mouvement);
    }
}

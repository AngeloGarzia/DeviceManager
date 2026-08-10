-- Quantité en stock par pièce détachée (0 = rupture).
ALTER TABLE device
  ADD COLUMN stock INT NOT NULL DEFAULT 0;

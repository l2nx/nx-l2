# reuse_ends_at is the wall-clock end in epoch ms: the reuse keeps running while the character is offline.
CREATE TABLE IF NOT EXISTS `character_item_reuse_save` (
  `charId` INT NOT NULL DEFAULT 0,
  `itemId` INT NOT NULL DEFAULT 0,
  `itemObjId` INT(3) NOT NULL DEFAULT 1,
  `reuseDelay` INT(8) NOT NULL DEFAULT 0,
  `reuse_ends_at` BIGINT NOT NULL,
  PRIMARY KEY (`charId`,`itemId`,`itemObjId`)
) DEFAULT CHARSET=latin1 COLLATE=latin1_general_ci;

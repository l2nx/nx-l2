# remaining_ms and tick_phase_ms are game-time durations, frozen while the pet is not summoned. A NULL remaining_ms
# is an effect without a duration.
CREATE TABLE IF NOT EXISTS `character_pet_skills_save` (
  `petObjItemId` INT NOT NULL DEFAULT 0,
  `skill_id` INT NOT NULL DEFAULT 0,
  `skill_level` INT(3) NOT NULL DEFAULT 1,
  `remaining_ms` BIGINT NULL,
  `tick_phase_ms` BIGINT NOT NULL DEFAULT 0,
  `buff_index` INT(2) NOT NULL DEFAULT 0,
  PRIMARY KEY (`petObjItemId`,`skill_id`,`skill_level`)
) DEFAULT CHARSET=latin1 COLLATE=latin1_general_ci;

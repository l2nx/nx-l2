# remaining_ms/tick_phase_ms: game-time durations, frozen while offline. NULL remaining_ms = no duration (toggle).
# reuse_ends_at: wall-clock epoch ms, NULL when not under reuse.
CREATE TABLE IF NOT EXISTS `character_skills_save` (
  `charId` INT NOT NULL DEFAULT 0,
  `skill_id` INT NOT NULL DEFAULT 0,
  `skill_level` INT(3) NOT NULL DEFAULT 1,
  `remaining_ms` BIGINT NULL,
  `tick_phase_ms` BIGINT NULL,
  `reuse_delay` INT(8) NOT NULL DEFAULT 0,
  `reuse_ends_at` BIGINT NULL,
  `restore_type` INT(1) NOT NULL DEFAULT 0,
  `class_index` INT(1) NOT NULL DEFAULT 0,
  `buff_index` INT(2) NOT NULL DEFAULT 0,
  PRIMARY KEY (`charId`,`skill_id`,`skill_level`,`class_index`)
) DEFAULT CHARSET=latin1 COLLATE=latin1_general_ci;

# RESTORE_SKILL_SAVE
CREATE INDEX idx_charId_classIndex_buffIndex ON character_skills_save (charId, class_index, buff_index);

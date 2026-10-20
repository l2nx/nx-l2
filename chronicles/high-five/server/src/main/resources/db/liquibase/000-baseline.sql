--liquibase formatted sql

--changeset game:000-baseline
CREATE COLLATION case_insensitive (provider = icu, locale = 'und-u-ks-level2', deterministic = false);

-- Crest ids go to the client, which caches crest images by id: an id must never point at another image, so it is
-- a persistent identity that is never reused (not a wire handle, not a UUID).
CREATE TABLE crests (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    crest_type text NOT NULL CHECK (crest_type IN ('PLEDGE', 'PLEDGE_LARGE', 'ALLY')),
    image bytea NOT NULL,
    -- Size limits enforced by the crest upload packets.
    CHECK (octet_length(image) > 0),
    CHECK (octet_length(image) <= CASE crest_type WHEN 'PLEDGE' THEN 256 WHEN 'ALLY' THEN 192 ELSE 2176 END)
);

CREATE TABLE clans (
    id                        uuid PRIMARY KEY,
    name                      text COLLATE case_insensitive NOT NULL CHECK (name <> ''),
    clan_level                integer NOT NULL DEFAULT 0 CHECK (clan_level BETWEEN 0 AND 11),
    -- Negative on purpose: a clan in debt loses its skills until the score is back to >= 0.
    reputation_score          integer NOT NULL DEFAULT 0,
    blood_alliance_count      integer NOT NULL DEFAULT 0 CHECK (blood_alliance_count >= 0),
    blood_oath_count          integer NOT NULL DEFAULT 0 CHECK (blood_oath_count >= 0),
    leader_character_id       uuid NOT NULL,
    new_leader_character_id   uuid,
    -- Alliance id is the id of the alliance leader clan; the leader clan points to itself.
    ally_clan_id              uuid REFERENCES clans (id) ON DELETE SET NULL,
    ally_name                 text COLLATE case_insensitive CHECK (ally_name <> ''),
    crest_id                  integer REFERENCES crests (id) ON DELETE SET NULL,
    crest_large_id            integer REFERENCES crests (id) ON DELETE SET NULL,
    ally_crest_id             integer REFERENCES crests (id) ON DELETE SET NULL,
    -- Clan hall whose auction the clan currently bids at (Mobius auction_bid_at; auction id = clan hall id).
    bid_clan_hall_id          integer,
    ally_penalty_ends_at      timestamptz,
    ally_penalty_type         integer CHECK (ally_penalty_type BETWEEN 1 AND 4),
    character_penalty_ends_at timestamptz,
    dissolving_ends_at        timestamptz,
    CONSTRAINT clans_name_key UNIQUE (name),
    CONSTRAINT clans_ally_penalty_check CHECK ((ally_penalty_ends_at IS NULL) = (ally_penalty_type IS NULL)),
    -- One-way only: ON DELETE SET NULL of ally_clan_id leaves ally_name for the alliance check to clear.
    CONSTRAINT clans_ally_check CHECK (ally_clan_id IS NULL OR ally_name IS NOT NULL)
);

CREATE INDEX clans_leader_character_id_idx ON clans (leader_character_id);

CREATE INDEX clans_new_leader_character_id_idx ON clans (new_leader_character_id);

CREATE INDEX clans_ally_clan_id_idx ON clans (ally_clan_id);

CREATE INDEX clans_crest_id_idx ON clans (crest_id);

CREATE INDEX clans_crest_large_id_idx ON clans (crest_large_id);

CREATE INDEX clans_ally_crest_id_idx ON clans (ally_crest_id);

CREATE INDEX clans_bid_clan_hall_id_idx ON clans (bid_clan_hall_id, id);

CREATE TABLE characters (
    id                          uuid PRIMARY KEY,
    account_id                  uuid NOT NULL,
    -- No length CHECK: CharacterCreate enforces 16, but admin rename does not, and a failing CHECK would drop the whole save.
    name                        text COLLATE case_insensitive NOT NULL UNIQUE CHECK (name <> ''),
    title                       text CHECK (title <> ''),
    title_color                 integer NOT NULL DEFAULT 15530402 CHECK (title_color BETWEEN 0 AND 16777215),
    race                        text NOT NULL CHECK (race IN ('HUMAN', 'ELF', 'DARK_ELF', 'ORC', 'DWARF', 'KAMAEL')),
    sex                         text NOT NULL CHECK (sex IN ('MALE', 'FEMALE')),
    face                        integer NOT NULL CHECK (face >= 0),
    hair_style                  integer NOT NULL CHECK (hair_style >= 0),
    hair_color                  integer NOT NULL CHECK (hair_color >= 0),
    class_id                    integer NOT NULL,
    base_class_id               integer NOT NULL,
    -- Level, exp and sp of the base class; a subclass keeps its own in character_subclasses.
    character_level             integer NOT NULL CHECK (character_level >= 1),
    exp                         bigint NOT NULL DEFAULT 0 CHECK (exp >= 0),
    exp_before_death            bigint NOT NULL DEFAULT 0 CHECK (exp_before_death >= 0),
    sp                          bigint NOT NULL DEFAULT 0 CHECK (sp >= 0),
    max_hp                      integer NOT NULL CHECK (max_hp >= 0),
    current_hp                  integer NOT NULL CHECK (current_hp >= 0),
    max_cp                      integer NOT NULL CHECK (max_cp >= 0),
    current_cp                  integer NOT NULL CHECK (current_cp >= 0),
    max_mp                      integer NOT NULL CHECK (max_mp >= 0),
    current_mp                  integer NOT NULL CHECK (current_mp >= 0),
    x                           integer NOT NULL,
    y                           integer NOT NULL,
    z                           integer NOT NULL,
    heading                     integer NOT NULL DEFAULT 0,
    karma                       integer NOT NULL DEFAULT 0 CHECK (karma >= 0),
    fame                        integer NOT NULL DEFAULT 0 CHECK (fame >= 0),
    pvp_kills                   integer NOT NULL DEFAULT 0 CHECK (pvp_kills >= 0),
    pk_kills                    integer NOT NULL DEFAULT 0 CHECK (pk_kills >= 0),
    access_level                integer NOT NULL DEFAULT 0,
    online_status               text NOT NULL DEFAULT 'OFFLINE' CHECK (online_status IN ('OFFLINE', 'ONLINE', 'DETACHED')),
    online_time_s               bigint NOT NULL DEFAULT 0 CHECK (online_time_s >= 0),
    -- Bit mask of one-time newbie rewards already given.
    newbie_flags                integer NOT NULL DEFAULT 1,
    noble                       boolean NOT NULL DEFAULT false,
    in_seven_signs_dungeon      boolean NOT NULL DEFAULT false,
    faction                     text CHECK (faction IN ('GOOD', 'EVIL')),
    language                    text CHECK (language <> ''),
    vitality_points             integer NOT NULL DEFAULT 0 CHECK (vitality_points >= 0),
    death_penalty_level         integer NOT NULL DEFAULT 0 CHECK (death_penalty_level >= 0),
    bookmark_slots              integer NOT NULL DEFAULT 0 CHECK (bookmark_slots >= 0),
    pc_cafe_points              integer NOT NULL DEFAULT 0 CHECK (pc_cafe_points >= 0),
    -- Clan membership. A deleted clan only clears clan_id: Clan.destroyClan resets the other clan fields of its members.
    clan_id                     uuid REFERENCES clans (id) ON DELETE SET NULL,
    clan_privileges             integer NOT NULL DEFAULT 0,
    -- 0 = main clan, -1 = academy, 100/200 = royal guards, 1001.. = knight orders: 0 is a real pledge, not "none".
    pledge_type                 integer NOT NULL DEFAULT 0,
    power_grade                 integer CHECK (power_grade BETWEEN 1 AND 9),
    academy_join_level          integer CHECK (academy_join_level > 0),
    apprentice_id               uuid REFERENCES characters (id) ON DELETE SET NULL,
    sponsor_id                  uuid REFERENCES characters (id) ON DELETE SET NULL,
    wants_peace                 boolean NOT NULL DEFAULT false,
    clan_join_penalty_ends_at   timestamptz,
    clan_create_penalty_ends_at timestamptz,
    delete_at                   timestamptz,
    created_at                  timestamptz NOT NULL,
    last_access_at              timestamptz NOT NULL
) WITH (fillfactor = 80, autovacuum_vacuum_scale_factor = 0.05);

CREATE INDEX characters_account_id_created_at_idx ON characters (account_id, created_at);

CREATE INDEX characters_clan_id_idx ON characters (clan_id);

CREATE INDEX characters_apprentice_id_idx ON characters (apprentice_id);

CREATE INDEX characters_sponsor_id_idx ON characters (sponsor_id);

CREATE TABLE mails (
    id uuid PRIMARY KEY,
    -- NULL: sent by the system (item auction, custom mail, news).
    sender_id uuid REFERENCES characters (id) ON DELETE CASCADE,
    -- No ON DELETE: deleting a character must first bounce its incoming mail (attachments back to the sender),
    -- a cascade would destroy the sender's items.
    receiver_id uuid NOT NULL REFERENCES characters (id),
    subject text CHECK (subject <> ''),
    content text CHECK (content <> ''),
    expires_at timestamptz NOT NULL,
    cod_price bigint CHECK (cod_price > 0),
    has_attachments boolean NOT NULL,
    unread boolean NOT NULL,
    deleted_by_sender boolean NOT NULL,
    deleted_by_receiver boolean NOT NULL,
    sender_type text NOT NULL CHECK (sender_type IN ('PLAYER', 'NEWS', 'NONE', 'ALEGRIA')),
    returned boolean NOT NULL
);

CREATE INDEX mails_sender_id_idx ON mails (sender_id);

CREATE INDEX mails_receiver_id_idx ON mails (receiver_id);

CREATE TABLE items (
    id uuid PRIMARY KEY,
    -- Character, or clan for CLANWH; no FK because the target table varies. NULL while the item is attached to a
    -- mail: the mail owns it in transit.
    owner_id uuid,
    item_template_id integer NOT NULL,
    count bigint NOT NULL CHECK (count > 0),
    enchant_level integer NOT NULL CHECK (enchant_level >= 0),
    location text NOT NULL CHECK (location IN (
        'INVENTORY', 'PAPERDOLL', 'WAREHOUSE', 'CLANWH', 'PET', 'PET_EQUIP', 'MAIL', 'FREIGHT')),
    -- Paperdoll slot for PAPERDOLL and PET_EQUIP, client sort order for INVENTORY.
    location_slot integer,
    mail_id uuid REFERENCES mails (id) ON DELETE CASCADE,
    -- Sent to the client as is; reused by lottery tickets, race tickets and pet control items.
    custom_type1 integer NOT NULL,
    custom_type2 integer NOT NULL,
    -- Shadow item mana; NULL for an ordinary item.
    mana_left integer CHECK (mana_left >= 0),
    expires_at timestamptz,
    CHECK ((location = 'MAIL') = (mail_id IS NOT NULL)),
    CHECK ((location = 'MAIL') = (owner_id IS NULL)),
    CHECK ((location IN ('INVENTORY', 'PAPERDOLL', 'PET_EQUIP')) = (location_slot IS NOT NULL))
) WITH (fillfactor = 80, autovacuum_vacuum_scale_factor = 0.05);

CREATE INDEX items_owner_id_location_idx ON items (owner_id, location);

CREATE INDEX items_item_template_id_idx ON items (item_template_id);

CREATE INDEX items_mail_id_idx ON items (mail_id) WHERE mail_id IS NOT NULL;

CREATE TABLE item_augmentations (
    item_id uuid PRIMARY KEY REFERENCES items (id) ON DELETE CASCADE,
    augmentation_id integer NOT NULL
);

CREATE TABLE item_elementals (
    item_id uuid NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    -- Elementals byte codes (FIRE 0 .. DARK 5), the same codes the protocol carries.
    element smallint NOT NULL CHECK (element BETWEEN 0 AND 5),
    element_value integer NOT NULL CHECK (element_value >= 0),
    PRIMARY KEY (item_id, element)
);

CREATE TABLE item_variables (
    item_id uuid NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    name text NOT NULL CHECK (name <> ''),
    content text NOT NULL CHECK (content <> ''),
    PRIMARY KEY (item_id, name)
) WITH (fillfactor = 80, autovacuum_vacuum_scale_factor = 0.05);

-- An item on the ground has no row in items (it has no owner); the id is the same item identity.
CREATE TABLE ground_items (
    id uuid PRIMARY KEY,
    item_template_id integer NOT NULL,
    count bigint NOT NULL CHECK (count > 0),
    enchant_level integer NOT NULL CHECK (enchant_level >= 0),
    x integer NOT NULL,
    y integer NOT NULL,
    z integer NOT NULL,
    -- NULL: exempt from auto-destroy (mercenary tickets, cursed weapons).
    dropped_at timestamptz,
    protected boolean NOT NULL
);

CREATE TABLE pets (
    item_id uuid PRIMARY KEY REFERENCES items (id) ON DELETE CASCADE,
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    name text COLLATE case_insensitive CHECK (name <> ''),
    pet_level integer NOT NULL CHECK (pet_level > 0),
    current_hp integer NOT NULL CHECK (current_hp >= 0),
    current_mp integer NOT NULL CHECK (current_mp >= 0),
    exp bigint NOT NULL CHECK (exp >= 0),
    sp bigint NOT NULL CHECK (sp >= 0),
    fed integer NOT NULL CHECK (fed >= 0),
    restore_on_login boolean NOT NULL
);

CREATE INDEX pets_character_id_idx ON pets (character_id);

-- remaining_ms and tick_phase_ms are game-time durations, frozen while the pet is not summoned. A NULL remaining_ms
-- is an effect without a duration.
CREATE TABLE character_pet_skill_saves (
    item_id uuid NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    skill_id integer NOT NULL,
    skill_level integer NOT NULL,
    remaining_ms bigint CHECK (remaining_ms >= 0),
    tick_phase_ms bigint NOT NULL DEFAULT 0 CHECK (tick_phase_ms >= 0),
    buff_index integer NOT NULL,
    PRIMARY KEY (item_id, skill_id, skill_level)
);

CREATE TABLE character_summons (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    summon_skill_id integer NOT NULL,
    current_hp integer NOT NULL CHECK (current_hp >= 0),
    current_mp integer NOT NULL CHECK (current_mp >= 0),
    life_remaining_ms bigint NOT NULL CHECK (life_remaining_ms > 0),
    PRIMARY KEY (character_id, summon_skill_id)
);

-- remaining_ms and tick_phase_ms are game-time durations, frozen while the servitor is not summoned. A NULL
-- remaining_ms is an effect without a duration.
CREATE TABLE character_summon_skill_saves (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    class_index integer NOT NULL CHECK (class_index >= 0),
    summon_skill_id integer NOT NULL,
    skill_id integer NOT NULL,
    skill_level integer NOT NULL,
    remaining_ms bigint CHECK (remaining_ms >= 0),
    tick_phase_ms bigint NOT NULL DEFAULT 0 CHECK (tick_phase_ms >= 0),
    buff_index integer NOT NULL,
    PRIMARY KEY (character_id, class_index, summon_skill_id, skill_id, skill_level)
);

-- Delivery queue filled by outside writers; rows are delivered and deleted when the receiver is online.
CREATE TABLE custom_mails (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now(),
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    subject text NOT NULL CHECK (subject <> ''),
    content text NOT NULL CHECK (content <> ''),
    -- "templateId count [enchant];templateId count;..."; NULL: no attachments.
    items text CHECK (items <> '')
);

CREATE INDEX custom_mails_character_id_idx ON custom_mails (character_id);

CREATE TABLE buylist_stocks (
    buylist_id integer NOT NULL,
    item_template_id integer NOT NULL,
    stock_count bigint NOT NULL CHECK (stock_count >= 0),
    restocks_at timestamptz,
    PRIMARY KEY (buylist_id, item_template_id)
);

-- The auction id is shown to the client (auction-ended system message), so it is a small never-reused integer.
CREATE TABLE item_auctions (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    npc_id integer NOT NULL,
    auction_item_id integer NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    state text NOT NULL CHECK (state IN ('CREATED', 'STARTED', 'FINISHED')),
    CHECK (starts_at < ends_at)
);

CREATE INDEX item_auctions_npc_id_idx ON item_auctions (npc_id);

CREATE TABLE item_auction_bids (
    item_auction_id integer NOT NULL REFERENCES item_auctions (id) ON DELETE CASCADE,
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    -- NULL: the bid was cancelled and is kept until the auction ends.
    bid_amount bigint CHECK (bid_amount > 0),
    PRIMARY KEY (item_auction_id, character_id)
);

CREATE INDEX item_auction_bids_character_id_idx ON item_auction_bids (character_id);

CREATE TABLE clan_hall_auctions (
    clan_hall_id integer PRIMARY KEY,
    -- NULL: the hall is sold by the NPC. No ON DELETE: a clan cannot be dissolved while it owns a hall, and the
    -- auction must refund its bidders before it goes.
    seller_clan_id uuid UNIQUE REFERENCES clans (id),
    starting_bid bigint NOT NULL CHECK (starting_bid > 0),
    current_bid bigint CHECK (current_bid > 0),
    ends_at timestamptz NOT NULL
);

CREATE TABLE clan_hall_auction_bids (
    clan_hall_id integer NOT NULL REFERENCES clan_hall_auctions (clan_hall_id) ON DELETE CASCADE,
    clan_id uuid NOT NULL UNIQUE REFERENCES clans (id) ON DELETE CASCADE,
    bid_amount bigint NOT NULL CHECK (bid_amount > 0),
    bid_at timestamptz NOT NULL,
    PRIMARY KEY (clan_hall_id, clan_id)
);

CREATE TABLE prime_shop_transactions (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    product_id integer NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    purchased_at timestamptz NOT NULL
);

CREATE INDEX prime_shop_transactions_character_id_idx ON prime_shop_transactions (character_id, purchased_at DESC);

-- The lottery number is shown to the client and stored on ticket items (custom_type1), so it is never reused.
CREATE TABLE lotteries (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ends_at timestamptz NOT NULL,
    prize_pool bigint NOT NULL CHECK (prize_pool >= 0),
    next_prize_pool bigint NOT NULL CHECK (next_prize_pool >= 0),
    finished boolean NOT NULL DEFAULT false,
    -- Bit masks of the drawn numbers, encoded like a ticket's enchant_level and custom_type2.
    winning_mask1 integer,
    winning_mask2 integer,
    first_prize bigint CHECK (first_prize >= 0),
    second_prize bigint CHECK (second_prize >= 0),
    third_prize bigint CHECK (third_prize >= 0),
    CHECK (num_nulls(winning_mask1, winning_mask2, first_prize, second_prize, third_prize)
        = CASE WHEN finished THEN 0 ELSE 5 END)
);

CREATE TABLE monster_race_bets (
    lane integer PRIMARY KEY CHECK (lane BETWEEN 1 AND 8),
    bet_total bigint NOT NULL CHECK (bet_total >= 0)
);

CREATE TABLE monster_race_results (
    race_number integer PRIMARY KEY CHECK (race_number > 0),
    winner_index integer NOT NULL CHECK (winner_index BETWEEN 0 AND 7),
    runner_up_index integer NOT NULL CHECK (runner_up_index BETWEEN 0 AND 7),
    winner_odds numeric(10, 2) NOT NULL CHECK (winner_odds >= 0)
);

CREATE TABLE cursed_weapons (
    item_template_id integer PRIMARY KEY,
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    -- The holder's karma and PK count before the weapon, restored when it leaves him.
    holder_karma integer NOT NULL CHECK (holder_karma >= 0),
    holder_pk_kills integer NOT NULL CHECK (holder_pk_kills >= 0),
    kill_count integer NOT NULL CHECK (kill_count >= 0),
    ends_at timestamptz NOT NULL
);

CREATE INDEX cursed_weapons_character_id_idx ON cursed_weapons (character_id);

CREATE TABLE clan_notices (
    clan_id uuid PRIMARY KEY REFERENCES clans (id) ON DELETE CASCADE,
    enabled boolean NOT NULL DEFAULT false,
    notice  text CHECK (notice <> '' AND char_length(notice) <= 8192)
);

CREATE TABLE clan_rank_privileges (
    clan_id        uuid NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    power_grade    integer NOT NULL CHECK (power_grade BETWEEN 1 AND 9),
    privilege_mask integer NOT NULL,
    PRIMARY KEY (clan_id, power_grade)
);

-- Surrogate key: the natural key has a nullable pledge_type (NULL = clan-wide skill), so it cannot be the
-- primary key, and change capture needs one.
CREATE TABLE clan_skills (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clan_id     uuid NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    skill_id    integer NOT NULL,
    skill_level integer NOT NULL CHECK (skill_level >= 1),
    pledge_type integer CHECK (pledge_type IN (0, -1, 100, 200, 1001, 1002, 2001, 2002)),
    CONSTRAINT clan_skills_clan_skill_pledge_key UNIQUE NULLS NOT DISTINCT (clan_id, skill_id, pledge_type)
);

CREATE TABLE clan_subpledges (
    clan_id             uuid NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    pledge_type         integer NOT NULL CHECK (pledge_type IN (-1, 100, 200, 1001, 1002, 2001, 2002)),
    name                text NOT NULL CHECK (name <> ''),
    leader_character_id uuid,
    PRIMARY KEY (clan_id, pledge_type)
);

CREATE INDEX clan_subpledges_leader_character_id_idx ON clan_subpledges (leader_character_id);

CREATE TABLE clan_wars (
    clan_id       uuid NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    enemy_clan_id uuid NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    PRIMARY KEY (clan_id, enemy_clan_id),
    CHECK (clan_id <> enemy_clan_id)
);

CREATE INDEX clan_wars_enemy_clan_id_idx ON clan_wars (enemy_clan_id);

-- Defaults are the in-game initial values: a state row is created by the first upsert, which names only the
-- columns it writes.
CREATE TABLE castle_states (
    castle_id            integer PRIMARY KEY,
    owner_clan_id        uuid REFERENCES clans (id) ON DELETE SET NULL,
    tax_percent          integer NOT NULL DEFAULT 0 CHECK (tax_percent BETWEEN 0 AND 100),
    treasury             bigint NOT NULL DEFAULT 0 CHECK (treasury >= 0),
    siege_at             timestamptz,
    registration_ends_at timestamptz,
    registration_over    boolean NOT NULL DEFAULT true,
    show_npc_crest       boolean NOT NULL DEFAULT false,
    ticket_buy_count     integer NOT NULL DEFAULT 0 CHECK (ticket_buy_count >= 0),
    CONSTRAINT castle_states_owner_clan_id_key UNIQUE (owner_clan_id)
);

CREATE TABLE castle_door_upgrades (
    door_id   integer PRIMARY KEY,
    castle_id integer NOT NULL,
    hp_ratio  integer NOT NULL CHECK (hp_ratio > 0)
);

CREATE INDEX castle_door_upgrades_castle_id_idx ON castle_door_upgrades (castle_id);

CREATE TABLE castle_trap_upgrades (
    castle_id   integer NOT NULL,
    tower_index integer NOT NULL CHECK (tower_index >= 0),
    trap_level  integer NOT NULL CHECK (trap_level >= 0),
    PRIMARY KEY (castle_id, tower_index)
);

CREATE TABLE castle_functions (
    castle_id      integer NOT NULL,
    function_type  integer NOT NULL CHECK (function_type BETWEEN 1 AND 5),
    function_level integer NOT NULL CHECK (function_level >= 0),
    lease          integer NOT NULL CHECK (lease >= 0),
    rate_ms        bigint NOT NULL CHECK (rate_ms > 0),
    -- NULL: the first fee has not been charged yet (Java endTime 0 / -1).
    ends_at        timestamptz,
    PRIMARY KEY (castle_id, function_type)
);

CREATE TABLE castle_manor_productions (
    castle_id             integer NOT NULL,
    seed_item_template_id integer NOT NULL,
    next_period           boolean NOT NULL,
    amount                bigint NOT NULL CHECK (amount >= 0),
    start_amount          bigint NOT NULL CHECK (start_amount >= 0),
    price                 bigint NOT NULL CHECK (price >= 0),
    PRIMARY KEY (castle_id, seed_item_template_id, next_period)
);

CREATE TABLE castle_manor_procurements (
    castle_id             integer NOT NULL,
    crop_item_template_id integer NOT NULL,
    next_period           boolean NOT NULL,
    amount                bigint NOT NULL CHECK (amount >= 0),
    start_amount          bigint NOT NULL CHECK (start_amount >= 0),
    price                 bigint NOT NULL CHECK (price >= 0),
    reward_type           integer NOT NULL CHECK (reward_type IN (1, 2)),
    PRIMARY KEY (castle_id, crop_item_template_id, next_period)
);

-- Only mercenaries placed by the castle owner; the designer-placed siege guards live in the datapack.
CREATE TABLE castle_mercenaries (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    castle_id integer NOT NULL,
    npc_id    integer NOT NULL,
    x         integer NOT NULL,
    y         integer NOT NULL,
    z         integer NOT NULL,
    heading   integer NOT NULL DEFAULT 0
);

CREATE INDEX castle_mercenaries_castle_id_idx ON castle_mercenaries (castle_id);

CREATE TABLE castle_siege_clans (
    castle_id integer NOT NULL,
    clan_id   uuid NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    side      text NOT NULL CHECK (side IN ('OWNER', 'DEFENDER', 'ATTACKER', 'DEFENDER_PENDING')),
    PRIMARY KEY (castle_id, clan_id)
);

CREATE INDEX castle_siege_clans_clan_id_idx ON castle_siege_clans (clan_id);

CREATE TABLE fort_states (
    fort_id              integer PRIMARY KEY,
    owner_clan_id        uuid REFERENCES clans (id) ON DELETE SET NULL,
    owned_since          timestamptz,
    siege_at             timestamptz,
    -- 0 not decided, 1 independent, 2 contracted with contracted_castle_id (Fort.setFortState).
    contract_state       integer NOT NULL DEFAULT 0 CHECK (contract_state IN (0, 1, 2)),
    contracted_castle_id integer,
    supply_level         integer NOT NULL DEFAULT 0 CHECK (supply_level >= 0),
    CONSTRAINT fort_states_owner_clan_id_key UNIQUE (owner_clan_id),
    CONSTRAINT fort_states_contract_check CHECK (contract_state <> 2 OR contracted_castle_id IS NOT NULL)
);

CREATE TABLE fort_door_upgrades (
    door_id integer PRIMARY KEY,
    fort_id integer NOT NULL,
    hp      integer NOT NULL CHECK (hp >= 0),
    p_def   integer NOT NULL CHECK (p_def >= 0),
    m_def   integer NOT NULL CHECK (m_def >= 0)
);

CREATE INDEX fort_door_upgrades_fort_id_idx ON fort_door_upgrades (fort_id);

CREATE TABLE fort_functions (
    fort_id        integer NOT NULL,
    function_type  integer NOT NULL CHECK (function_type BETWEEN 1 AND 5),
    function_level integer NOT NULL CHECK (function_level >= 0),
    lease          integer NOT NULL CHECK (lease >= 0),
    rate_ms        bigint NOT NULL CHECK (rate_ms > 0),
    ends_at        timestamptz,
    PRIMARY KEY (fort_id, function_type)
);

CREATE TABLE fort_siege_clans (
    fort_id integer NOT NULL,
    clan_id uuid NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    PRIMARY KEY (fort_id, clan_id)
);

CREATE INDEX fort_siege_clans_clan_id_idx ON fort_siege_clans (clan_id);

CREATE TABLE clan_hall_states (
    clan_hall_id  integer PRIMARY KEY,
    owner_clan_id uuid REFERENCES clans (id) ON DELETE SET NULL,
    paid_until    timestamptz,
    paid          boolean NOT NULL DEFAULT false,
    CONSTRAINT clan_hall_states_owner_clan_id_key UNIQUE (owner_clan_id)
);

-- Serves auctionable and siegable halls alike, so clan_hall_id has no foreign key.
CREATE TABLE clan_hall_functions (
    clan_hall_id   integer NOT NULL,
    function_type  integer NOT NULL CHECK (function_type BETWEEN 1 AND 8),
    function_level integer NOT NULL CHECK (function_level >= 0),
    lease          integer NOT NULL CHECK (lease >= 0),
    rate_ms        bigint NOT NULL CHECK (rate_ms > 0),
    ends_at        timestamptz,
    PRIMARY KEY (clan_hall_id, function_type)
);

CREATE TABLE clan_hall_siege_attackers (
    clan_hall_id integer NOT NULL,
    clan_id      uuid NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    PRIMARY KEY (clan_hall_id, clan_id)
);

CREATE INDEX clan_hall_siege_attackers_clan_id_idx ON clan_hall_siege_attackers (clan_id);

CREATE TABLE siegable_clan_hall_states (
    clan_hall_id  integer PRIMARY KEY,
    owner_clan_id uuid REFERENCES clans (id) ON DELETE SET NULL,
    next_siege_at timestamptz,
    CONSTRAINT siegable_clan_hall_states_owner_clan_id_key UNIQUE (owner_clan_id)
);

CREATE TABLE siegable_hall_flag_war_attackers (
    clan_hall_id integer NOT NULL,
    clan_id      uuid NOT NULL REFERENCES clans (id) ON DELETE CASCADE,
    flag_npc_id  integer NOT NULL,
    -- NULL until the clan leader picks the allied NPC.
    ally_npc_id  integer,
    PRIMARY KEY (clan_hall_id, clan_id),
    CONSTRAINT siegable_hall_flag_war_attackers_flag_key UNIQUE (clan_hall_id, flag_npc_id)
);

CREATE INDEX siegable_hall_flag_war_attackers_clan_id_idx ON siegable_hall_flag_war_attackers (clan_id);

CREATE TABLE siegable_hall_flag_war_attacker_members (
    clan_hall_id integer NOT NULL,
    clan_id      uuid NOT NULL,
    character_id uuid NOT NULL,
    PRIMARY KEY (clan_hall_id, clan_id, character_id),
    FOREIGN KEY (clan_hall_id, clan_id)
        REFERENCES siegable_hall_flag_war_attackers (clan_hall_id, clan_id) ON DELETE CASCADE
);

CREATE INDEX siegable_hall_flag_war_attacker_members_character_id_idx
    ON siegable_hall_flag_war_attacker_members (character_id);

CREATE TABLE rainbow_springs_attackers (
    clan_id           uuid PRIMARY KEY REFERENCES clans (id) ON DELETE CASCADE,
    war_decree_count  bigint NOT NULL CHECK (war_decree_count >= 0)
);

-- No row: the territory owns only its own ward (ARRAY[territory_id]); the loader applies it.
CREATE TABLE territory_states (
    territory_id             integer PRIMARY KEY,
    owned_ward_territory_ids integer[] NOT NULL CHECK (cardinality(owned_ward_territory_ids) <= 9)
);

-- registrant_id is a clan or, for a mercenary, a character: no foreign key (target varies).
CREATE TABLE territory_registrations (
    castle_id       integer NOT NULL,
    registrant_id   uuid NOT NULL,
    registrant_type text NOT NULL CHECK (registrant_type IN ('CLAN', 'MERCENARY')),
    PRIMARY KEY (castle_id, registrant_id)
);

CREATE INDEX territory_registrations_registrant_id_idx ON territory_registrations (registrant_id);

CREATE TABLE character_subclasses (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    class_index  integer NOT NULL CHECK (class_index > 0),
    class_id     integer NOT NULL,
    exp          bigint NOT NULL DEFAULT 0 CHECK (exp >= 0),
    sp           bigint NOT NULL DEFAULT 0 CHECK (sp >= 0),
    class_level  integer NOT NULL DEFAULT 40 CHECK (class_level >= 1),
    PRIMARY KEY (character_id, class_index),
    UNIQUE (character_id, class_id)
);

CREATE TABLE character_skills (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    class_index  integer NOT NULL CHECK (class_index >= 0),
    skill_id     integer NOT NULL,
    skill_level  integer NOT NULL CHECK (skill_level >= 1),
    PRIMARY KEY (character_id, class_index, skill_id)
);

CREATE TABLE character_skill_saves (
    character_id  uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    class_index   integer NOT NULL CHECK (class_index >= 0),
    skill_id      integer NOT NULL,
    skill_level   integer NOT NULL CHECK (skill_level >= 1),
    restore_type  text NOT NULL CHECK (restore_type IN ('EFFECT', 'REUSE')),
    -- Game-time durations, frozen while offline; NULL remaining_ms on an EFFECT row = no duration (toggle).
    remaining_ms  bigint CHECK (remaining_ms >= 0),
    tick_phase_ms bigint CHECK (tick_phase_ms >= 0),
    reuse_delay_ms bigint NOT NULL DEFAULT 0 CHECK (reuse_delay_ms >= 0),
    -- Wall-clock instant: the reuse keeps running while the character is offline.
    reuse_ends_at timestamptz,
    buff_order    integer NOT NULL CHECK (buff_order > 0),
    PRIMARY KEY (character_id, class_index, skill_id, skill_level),
    CHECK (restore_type = 'EFFECT' OR (reuse_ends_at IS NOT NULL AND remaining_ms IS NULL AND tick_phase_ms IS NULL))
) WITH (fillfactor = 80, autovacuum_vacuum_scale_factor = 0.05);

CREATE TABLE character_shortcuts (
    character_id  uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    class_index   integer NOT NULL CHECK (class_index >= 0),
    page          integer NOT NULL CHECK (page >= 0),
    slot          integer NOT NULL CHECK (slot >= 0),
    shortcut_type text NOT NULL CHECK (shortcut_type IN ('NONE', 'ITEM', 'SKILL', 'ACTION', 'MACRO', 'RECIPE', 'BOOKMARK')),
    -- Skill, action, macro, recipe or bookmark id; an ITEM shortcut points at the item instance through item_id.
    target_id     integer,
    item_id       uuid REFERENCES items (id) ON DELETE CASCADE,
    skill_level   integer CHECK (skill_level >= 1),
    PRIMARY KEY (character_id, class_index, page, slot),
    CHECK ((shortcut_type = 'ITEM') = (item_id IS NOT NULL)),
    CHECK ((shortcut_type = 'ITEM') = (target_id IS NULL)),
    CHECK (skill_level IS NULL OR shortcut_type = 'SKILL')
);

CREATE INDEX character_shortcuts_item_id_idx ON character_shortcuts (item_id);

CREATE TABLE character_macros (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    macro_id     integer NOT NULL,
    icon         integer NOT NULL,
    name         text CHECK (name <> ''),
    description  text CHECK (description <> ''),
    acronym      text CHECK (acronym <> ''),
    -- Serialized MacroCmd list: "type,d1,d2[,command];..." as MacroList writes it.
    commands     text CHECK (commands <> ''),
    PRIMARY KEY (character_id, macro_id)
);

CREATE TABLE character_hennas (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    class_index  integer NOT NULL CHECK (class_index >= 0),
    slot         integer NOT NULL CHECK (slot BETWEEN 1 AND 3),
    henna_id     integer NOT NULL,
    PRIMARY KEY (character_id, class_index, slot)
);

CREATE TABLE character_quests (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    quest_name   text NOT NULL CHECK (quest_name <> ''),
    -- Quest variable name; the reserved name '<state>' holds the quest state.
    name         text NOT NULL CHECK (name <> ''),
    content      text CHECK (content <> ''),
    PRIMARY KEY (character_id, quest_name, name)
);

CREATE TABLE character_variables (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    name         text NOT NULL CHECK (name <> ''),
    content      text CHECK (content <> ''),
    PRIMARY KEY (character_id, name)
) WITH (fillfactor = 80, autovacuum_vacuum_scale_factor = 0.05);

CREATE TABLE character_recipes (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    -- Common recipes are shared by all classes and stored with class_index 0.
    class_index  integer NOT NULL CHECK (class_index >= 0),
    recipe_id    integer NOT NULL,
    dwarven      boolean NOT NULL,
    PRIMARY KEY (character_id, class_index, recipe_id),
    CHECK (dwarven OR class_index = 0)
);

CREATE TABLE character_recipe_shop_items (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    recipe_id    integer NOT NULL,
    price        bigint NOT NULL CHECK (price >= 0),
    sort_order   integer NOT NULL CHECK (sort_order >= 0),
    PRIMARY KEY (character_id, recipe_id)
);

CREATE TABLE character_recommendations (
    character_id       uuid PRIMARY KEY REFERENCES characters (id) ON DELETE CASCADE,
    received           integer NOT NULL DEFAULT 0 CHECK (received >= 0),
    left_to_give       integer NOT NULL DEFAULT 0 CHECK (left_to_give >= 0),
    bonus_time_left_ms bigint NOT NULL DEFAULT 0 CHECK (bonus_time_left_ms >= 0)
);

CREATE TABLE character_teleport_bookmarks (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    bookmark_id  integer NOT NULL,
    x            integer NOT NULL,
    y            integer NOT NULL,
    z            integer NOT NULL,
    icon         integer NOT NULL,
    tag          text CHECK (tag <> ''),
    name         text NOT NULL CHECK (name <> ''),
    PRIMARY KEY (character_id, bookmark_id)
);

CREATE TABLE character_item_reuse_saves (
    character_id     uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    item_id          uuid NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    item_template_id integer NOT NULL,
    reuse_delay_ms   bigint NOT NULL CHECK (reuse_delay_ms >= 0),
    reuse_ends_at    timestamptz NOT NULL,
    PRIMARY KEY (character_id, item_id)
);

CREATE INDEX character_item_reuse_saves_item_id_idx ON character_item_reuse_saves (item_id);

CREATE TABLE character_instance_times (
    character_id         uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    instance_template_id integer NOT NULL,
    reenter_at           timestamptz NOT NULL,
    PRIMARY KEY (character_id, instance_template_id)
);

CREATE TABLE character_raid_points (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    npc_id       integer NOT NULL,
    points       integer NOT NULL CHECK (points >= 0),
    PRIMARY KEY (character_id, npc_id)
);

-- Rows are delivered by an outside writer (shop); item_num is the id the client withdraws by.
CREATE TABLE character_premium_items (
    character_id     uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    item_num         integer NOT NULL,
    item_template_id integer NOT NULL,
    item_count       bigint NOT NULL CHECK (item_count > 0),
    sender_name      text NOT NULL CHECK (sender_name <> ''),
    PRIMARY KEY (character_id, item_num)
);

-- Owned by a character or, with TransmogConfig.TRANSMOG_SHARE_ACCOUNT, by an account: two columns instead of one
-- polymorphic id so that a character's unlocks still cascade on delete.
CREATE TABLE character_transmogs (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id     uuid REFERENCES characters (id) ON DELETE CASCADE,
    account_id       uuid,
    item_template_id integer NOT NULL,
    CHECK (num_nonnulls(character_id, account_id) = 1),
    UNIQUE NULLS NOT DISTINCT (character_id, account_id, item_template_id)
);

CREATE INDEX character_transmogs_account_id_idx ON character_transmogs (account_id) WHERE account_id IS NOT NULL;

CREATE TABLE character_contacts (
    character_id         uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    contact_character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    PRIMARY KEY (character_id, contact_character_id)
);

CREATE INDEX character_contacts_contact_character_id_idx ON character_contacts (contact_character_id);

CREATE TABLE character_relations (
    character_id        uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    target_character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    relation            text NOT NULL CHECK (relation IN ('FRIEND', 'BLOCK')),
    PRIMARY KEY (character_id, target_character_id, relation)
);

CREATE INDEX character_relations_target_character_id_idx ON character_relations (target_character_id);

CREATE TABLE bot_reports (
    bot_character_id      uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    reporter_character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    reported_at           timestamptz NOT NULL,
    PRIMARY KEY (bot_character_id, reporter_character_id)
);

CREATE INDEX bot_reports_reporter_character_id_idx ON bot_reports (reporter_character_id);

CREATE TABLE character_offline_play_settings (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    entry_type   text NOT NULL CHECK (entry_type IN ('ACTIVE_SOULSHOT', 'AUTO_SUPPLY_ITEM', 'AUTO_BUFF', 'AUTO_SKILL',
                                                     'AUTO_ACTION', 'AUTO_POTION_ITEM', 'CUSTOM_AUTO_POTION_SYSTEM')),
    -- Item template, skill or action id by entry_type; NULL for CUSTOM_AUTO_POTION_SYSTEM, which is a flag.
    target_id    integer,
    UNIQUE NULLS NOT DISTINCT (character_id, entry_type, target_id),
    CHECK ((entry_type = 'CUSTOM_AUTO_POTION_SYSTEM') = (target_id IS NULL))
);

CREATE TABLE character_offline_play_party_members (
    character_id        uuid PRIMARY KEY REFERENCES characters (id) ON DELETE CASCADE,
    leader_character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    distribution_type   text NOT NULL CHECK (distribution_type IN ('FINDERS_KEEPERS', 'RANDOM', 'RANDOM_INCLUDING_SPOIL',
                                                                   'BY_TURN', 'BY_TURN_INCLUDING_SPOIL')),
    CHECK (character_id <> leader_character_id)
);

CREATE INDEX character_offline_play_party_members_leader_character_id_idx
    ON character_offline_play_party_members (leader_character_id);

CREATE TABLE character_offline_trades (
    character_id uuid PRIMARY KEY REFERENCES characters (id) ON DELETE CASCADE,
    started_at   timestamptz NOT NULL,
    store_type   text NOT NULL CHECK (store_type IN ('SELL', 'PACKAGE_SELL', 'SELL_BUFFS', 'BUY', 'MANUFACTURE')),
    title        text CHECK (title <> '')
);

CREATE TABLE character_offline_trade_items (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- References characters, not character_offline_trades: OfflineTraderTable writes the items before the trade row.
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    -- SELL and PACKAGE_SELL list item instances (item_id); BUY lists item templates, SELL_BUFFS skills and
    -- MANUFACTURE recipes (target_id).
    item_id      uuid REFERENCES items (id) ON DELETE CASCADE,
    target_id    integer,
    item_count   bigint NOT NULL DEFAULT 0 CHECK (item_count >= 0),
    price        bigint NOT NULL CHECK (price >= 0),
    CHECK (num_nonnulls(item_id, target_id) = 1)
);

CREATE INDEX character_offline_trade_items_character_id_idx ON character_offline_trade_items (character_id);

CREATE INDEX character_offline_trade_items_item_id_idx ON character_offline_trade_items (item_id);

CREATE TABLE account_variables (
    account_id uuid NOT NULL,
    name       text NOT NULL CHECK (name <> ''),
    content    text CHECK (content <> ''),
    PRIMARY KEY (account_id, name)
);

-- AccountVariables.deleteVariable drops one key for every account (daily and weekly resets).
CREATE INDEX account_variables_name_idx ON account_variables (name);

CREATE TABLE account_premiums (
    account_id uuid PRIMARY KEY,
    ends_at    timestamptz NOT NULL
);

-- Premium of a single character, for AccountWidePremium=false.
CREATE TABLE character_premiums (
    character_id uuid PRIMARY KEY REFERENCES characters (id) ON DELETE CASCADE,
    ends_at      timestamptz NOT NULL
);

CREATE TABLE character_rebirths (
    character_id       uuid PRIMARY KEY REFERENCES characters (id) ON DELETE CASCADE,
    rebirth_count      integer NOT NULL DEFAULT 0 CHECK (rebirth_count >= 0),
    selected_skill_ids integer[] NOT NULL DEFAULT '{}'
);

CREATE TABLE buffer_schemes (
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    -- SchemeBufferTable keys schemes case-insensitively.
    name         text COLLATE case_insensitive NOT NULL CHECK (name <> ''),
    skill_ids    integer[] NOT NULL DEFAULT '{}',
    PRIMARY KEY (character_id, name)
);

CREATE TABLE bbs_favorites (
    -- Shown to the client in the "_bbsdelfav_<id>" bypass.
    id           integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id uuid NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    title        text NOT NULL CHECK (title <> ''),
    bypass       text NOT NULL CHECK (bypass <> ''),
    added_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (character_id, bypass)
);

-- Spawn points and respawn delays live in datapack XML; only the state is stored.
CREATE TABLE raid_boss_states (
    npc_id     integer          PRIMARY KEY,
    respawn_at timestamptz,
    current_hp double precision NOT NULL CHECK (current_hp >= 0),
    current_mp double precision NOT NULL CHECK (current_mp >= 0)
);

-- Grand bosses: status codes are per-boss script constants (0..3), not one shared enum.
-- Position and HP/MP are those of a living boss; NULL means "spawn at the datapack point at full HP/MP".
CREATE TABLE grand_boss_states (
    npc_id     integer          PRIMARY KEY,
    status     smallint         NOT NULL CHECK (status BETWEEN 0 AND 3),
    respawn_at timestamptz,
    current_hp double precision CHECK (current_hp >= 0),
    current_mp double precision CHECK (current_mp >= 0),
    x          integer,
    y          integer,
    z          integer,
    heading    integer          CHECK (heading BETWEEN 0 AND 65535),
    CHECK ((current_hp IS NULL) = (current_mp IS NULL)),
    CHECK (num_nulls(x, y, z, heading) IN (0, 4))
);

CREATE TABLE grand_boss_zone_characters (
    character_id uuid    NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    zone_id      integer NOT NULL,
    PRIMARY KEY (character_id, zone_id)
);

CREATE TABLE olympiad_status (
    id                    smallint    PRIMARY KEY CHECK (id = 0),
    current_cycle         integer     NOT NULL CHECK (current_cycle >= 1),
    period                text        NOT NULL CHECK (period IN ('COMPETITION', 'VALIDATION')),
    olympiad_ends_at      timestamptz,
    validation_ends_at    timestamptz,
    next_weekly_change_at timestamptz
);

CREATE TABLE olympiad_nobles (
    character_id                       uuid    PRIMARY KEY REFERENCES characters (id) ON DELETE CASCADE,
    class_id                           integer NOT NULL,
    olympiad_points                    integer NOT NULL CHECK (olympiad_points >= 0),
    competitions_done                  integer NOT NULL CHECK (competitions_done >= 0),
    competitions_won                   integer NOT NULL CHECK (competitions_won >= 0),
    competitions_lost                  integer NOT NULL CHECK (competitions_lost >= 0),
    competitions_drawn                 integer NOT NULL CHECK (competitions_drawn >= 0),
    competitions_done_week             integer NOT NULL CHECK (competitions_done_week >= 0),
    competitions_done_week_classed     integer NOT NULL CHECK (competitions_done_week_classed >= 0),
    competitions_done_week_non_classed integer NOT NULL CHECK (competitions_done_week_non_classed >= 0),
    competitions_done_week_team        integer NOT NULL CHECK (competitions_done_week_team >= 0)
);

-- Snapshot of olympiad_nobles taken at the end of each olympiad period (ranking, hero selection, points claim).
CREATE TABLE olympiad_nobles_eom (
    character_id       uuid    PRIMARY KEY REFERENCES characters (id) ON DELETE CASCADE,
    class_id           integer NOT NULL,
    olympiad_points    integer NOT NULL CHECK (olympiad_points >= 0),
    competitions_done  integer NOT NULL CHECK (competitions_done >= 0),
    competitions_won   integer NOT NULL CHECK (competitions_won >= 0),
    competitions_lost  integer NOT NULL CHECK (competitions_lost >= 0),
    competitions_drawn integer NOT NULL CHECK (competitions_drawn >= 0)
);

CREATE TABLE olympiad_fights (
    id                  bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_character_id  uuid        NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    second_character_id uuid        NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    first_class_id      integer     NOT NULL,
    second_class_id     integer     NOT NULL,
    -- NULL is a draw.
    winner              smallint    CHECK (winner IN (1, 2)),
    started_at          timestamptz NOT NULL,
    duration_ms         bigint      NOT NULL CHECK (duration_ms >= 0),
    classed             boolean     NOT NULL,
    CHECK (first_character_id <> second_character_id)
);

CREATE INDEX olympiad_fights_first_character_idx ON olympiad_fights (first_character_id, started_at);

CREATE INDEX olympiad_fights_second_character_idx ON olympiad_fights (second_character_id, started_at);

CREATE TABLE heroes (
    character_id uuid    PRIMARY KEY REFERENCES characters (id) ON DELETE CASCADE,
    class_id     integer NOT NULL,
    hero_count   integer NOT NULL CHECK (hero_count >= 0),
    played       boolean NOT NULL,
    claimed      boolean NOT NULL,
    message      text    CHECK (message <> '' AND char_length(message) <= 300)
);

CREATE TABLE hero_diary_entries (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id uuid        NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    recorded_at  timestamptz NOT NULL,
    action       text        NOT NULL CHECK (action IN ('RAID_KILLED', 'HERO_GAINED', 'CASTLE_TAKEN')),
    npc_id       integer,
    castle_id    integer,
    CHECK ((action = 'RAID_KILLED') = (npc_id IS NOT NULL)),
    CHECK ((action = 'CASTLE_TAKEN') = (castle_id IS NOT NULL))
);

CREATE INDEX hero_diary_entries_character_idx ON hero_diary_entries (character_id, recorded_at);

CREATE TABLE seven_signs_status (
    id                  smallint    PRIMARY KEY CHECK (id = 0),
    current_cycle       integer     NOT NULL CHECK (current_cycle >= 1),
    festival_cycle      integer     NOT NULL CHECK (festival_cycle >= 1),
    active_period       text        NOT NULL
        CHECK (active_period IN ('COMP_RECRUITING', 'COMPETITION', 'COMP_RESULTS', 'SEAL_VALIDATION')),
    saved_at            timestamptz,
    previous_winner     text        CHECK (previous_winner IN ('DAWN', 'DUSK')),
    dawn_stone_score    bigint      NOT NULL DEFAULT 0 CHECK (dawn_stone_score >= 0),
    dawn_festival_score integer     NOT NULL DEFAULT 0 CHECK (dawn_festival_score >= 0),
    dusk_stone_score    bigint      NOT NULL DEFAULT 0 CHECK (dusk_stone_score >= 0),
    dusk_festival_score integer     NOT NULL DEFAULT 0 CHECK (dusk_festival_score >= 0),
    avarice_owner       text        CHECK (avarice_owner IN ('DAWN', 'DUSK')),
    gnosis_owner        text        CHECK (gnosis_owner IN ('DAWN', 'DUSK')),
    strife_owner        text        CHECK (strife_owner IN ('DAWN', 'DUSK')),
    avarice_dawn_score  integer     NOT NULL DEFAULT 0 CHECK (avarice_dawn_score >= 0),
    gnosis_dawn_score   integer     NOT NULL DEFAULT 0 CHECK (gnosis_dawn_score >= 0),
    strife_dawn_score   integer     NOT NULL DEFAULT 0 CHECK (strife_dawn_score >= 0),
    avarice_dusk_score  integer     NOT NULL DEFAULT 0 CHECK (avarice_dusk_score >= 0),
    gnosis_dusk_score   integer     NOT NULL DEFAULT 0 CHECK (gnosis_dusk_score >= 0),
    strife_dusk_score   integer     NOT NULL DEFAULT 0 CHECK (strife_dusk_score >= 0),
    accumulated_bonus_0 integer     NOT NULL DEFAULT 0 CHECK (accumulated_bonus_0 >= 0),
    accumulated_bonus_1 integer     NOT NULL DEFAULT 0 CHECK (accumulated_bonus_1 >= 0),
    accumulated_bonus_2 integer     NOT NULL DEFAULT 0 CHECK (accumulated_bonus_2 >= 0),
    accumulated_bonus_3 integer     NOT NULL DEFAULT 0 CHECK (accumulated_bonus_3 >= 0),
    accumulated_bonus_4 integer     NOT NULL DEFAULT 0 CHECK (accumulated_bonus_4 >= 0)
);

CREATE TABLE seven_signs_participants (
    character_id         uuid    PRIMARY KEY REFERENCES characters (id) ON DELETE CASCADE,
    -- NULL after the cycle reset until the character signs up again.
    cabal                text    CHECK (cabal IN ('DAWN', 'DUSK')),
    seal                 text    CHECK (seal IN ('AVARICE', 'GNOSIS', 'STRIFE')),
    red_stones           integer NOT NULL DEFAULT 0 CHECK (red_stones >= 0),
    green_stones         integer NOT NULL DEFAULT 0 CHECK (green_stones >= 0),
    blue_stones          integer NOT NULL DEFAULT 0 CHECK (blue_stones >= 0),
    ancient_adena_amount bigint  NOT NULL DEFAULT 0 CHECK (ancient_adena_amount >= 0),
    contribution_score   bigint  NOT NULL DEFAULT 0 CHECK (contribution_score >= 0),
    CHECK ((cabal IS NULL) = (seal IS NULL))
);

CREATE TABLE seven_signs_festival_scores (
    cycle        integer     NOT NULL CHECK (cycle >= 1),
    festival_id  smallint    NOT NULL CHECK (festival_id BETWEEN 0 AND 4),
    cabal        text        NOT NULL CHECK (cabal IN ('DAWN', 'DUSK')),
    scored_at    timestamptz,
    score        integer     NOT NULL CHECK (score >= 0),
    member_ids   uuid[]      NOT NULL,
    PRIMARY KEY (cycle, festival_id, cabal)
);

CREATE TABLE fishing_championship_entries (
    character_id uuid             NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    -- false: entry of the running period; true: winner of the previous period.
    winner       boolean          NOT NULL,
    fish_length  double precision NOT NULL CHECK (fish_length > 0),
    rewarded     boolean          NOT NULL,
    PRIMARY KEY (character_id, winner),
    CHECK (winner OR NOT rewarded)
);

CREATE TABLE announcements (
    id                integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    announcement_type text   NOT NULL
        CHECK (announcement_type IN ('NORMAL', 'CRITICAL', 'EVENT', 'AUTO_NORMAL', 'AUTO_CRITICAL')),
    initial_delay_ms  bigint CHECK (initial_delay_ms >= 0),
    interval_ms       bigint CHECK (interval_ms >= 10000),
    -- NULL repeats forever.
    repeat_count      integer CHECK (repeat_count > 0),
    author            text   NOT NULL CHECK (author <> ''),
    content           text   NOT NULL CHECK (content <> ''),
    CHECK ((announcement_type IN ('AUTO_NORMAL', 'AUTO_CRITICAL'))
        = (initial_delay_ms IS NOT NULL AND interval_ms IS NOT NULL)),
    CHECK (repeat_count IS NULL OR announcement_type IN ('AUTO_NORMAL', 'AUTO_CRITICAL'))
);

CREATE TABLE global_variables (
    name    text PRIMARY KEY CHECK (name <> ''),
    content text NOT NULL CHECK (content <> '')
);

-- Exactly one target column is set, matching affect. Accounts live in another schema: no FK.
CREATE TABLE punishments (
    id              bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    affect          text        NOT NULL CHECK (affect IN ('ACCOUNT', 'CHARACTER', 'IP', 'HWID')),
    account_id      uuid,
    character_id    uuid        REFERENCES characters (id) ON DELETE CASCADE,
    ip              inet,
    hwid            text        CHECK (hwid <> ''),
    punishment_type text        NOT NULL CHECK (punishment_type IN ('BAN', 'CHAT_BAN', 'PARTY_BAN', 'JAIL')),
    -- NULL is permanent; ending a punishment early sets it to the end time.
    expires_at      timestamptz,
    reason          text        CHECK (reason <> ''),
    punished_by     text        NOT NULL CHECK (punished_by <> ''),
    CHECK (num_nonnulls(account_id, character_id, ip, hwid) = 1),
    CHECK ((affect = 'ACCOUNT') = (account_id IS NOT NULL)),
    CHECK ((affect = 'CHARACTER') = (character_id IS NOT NULL)),
    CHECK ((affect = 'IP') = (ip IS NOT NULL)),
    CHECK ((affect = 'HWID') = (hwid IS NOT NULL))
);

CREATE INDEX punishments_character_idx ON punishments (character_id);

CREATE TABLE petition_feedbacks (
    id               bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id     uuid        NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    -- The rated GM; the feedback outlives a deleted GM character.
    gm_character_id  uuid        REFERENCES characters (id) ON DELETE SET NULL,
    rating           smallint    NOT NULL CHECK (rating BETWEEN 0 AND 4),
    message          text        CHECK (message <> ''),
    created_at       timestamptz NOT NULL
);

CREATE INDEX petition_feedbacks_character_idx ON petition_feedbacks (character_id);

CREATE INDEX petition_feedbacks_gm_character_idx ON petition_feedbacks (gm_character_id);

-- The owner column matches forum_type: CLAN -> clan_id, MAIL -> character_id, MEMO -> account_id
-- (the memo board is per account). Owned forums have no name: it was the owner's name, used for lookup.
CREATE TABLE forums (
    id           integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id    integer REFERENCES forums (id) ON DELETE CASCADE,
    name         text    CHECK (name <> ''),
    forum_type   text    NOT NULL CHECK (forum_type IN ('ROOT', 'NORMAL', 'CLAN', 'MEMO', 'MAIL')),
    permission   text    NOT NULL CHECK (permission IN ('INVISIBLE', 'ALL', 'CLANMEMBERONLY', 'OWNERONLY')),
    clan_id      uuid    UNIQUE REFERENCES clans (id) ON DELETE CASCADE,
    character_id uuid    UNIQUE REFERENCES characters (id) ON DELETE CASCADE,
    account_id   uuid    UNIQUE,
    CHECK ((forum_type = 'ROOT') = (parent_id IS NULL)),
    CHECK ((forum_type IN ('ROOT', 'NORMAL')) = (name IS NOT NULL)),
    CHECK ((forum_type = 'CLAN') = (clan_id IS NOT NULL)),
    CHECK ((forum_type = 'MAIL') = (character_id IS NOT NULL)),
    CHECK ((forum_type = 'MEMO') = (account_id IS NOT NULL))
);

CREATE INDEX forums_parent_idx ON forums (parent_id);

CREATE UNIQUE INDEX forums_root_name_key ON forums (name) WHERE parent_id IS NULL;

CREATE TABLE topics (
    id           integer     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    forum_id     integer     NOT NULL REFERENCES forums (id) ON DELETE CASCADE,
    character_id uuid        NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    title        text        NOT NULL CHECK (title <> ''),
    topic_type   text        NOT NULL CHECK (topic_type IN ('NORMAL', 'MEMO')),
    created_at   timestamptz NOT NULL
);

CREATE INDEX topics_forum_idx ON topics (forum_id);

CREATE INDEX topics_character_idx ON topics (character_id);

CREATE TABLE posts (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    topic_id     integer     NOT NULL REFERENCES topics (id) ON DELETE CASCADE,
    character_id uuid        NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    content      text        CHECK (content <> ''),
    created_at   timestamptz NOT NULL
);

CREATE INDEX posts_topic_idx ON posts (topic_id);

CREATE INDEX posts_character_idx ON posts (character_id);

-- A character is in at most one couple; the reverse pairing (A in first, B in second and vice versa) is checked in Java.
CREATE TABLE couples (
    id                  bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_character_id  uuid        NOT NULL UNIQUE REFERENCES characters (id) ON DELETE CASCADE,
    second_character_id uuid        NOT NULL UNIQUE REFERENCES characters (id) ON DELETE CASCADE,
    engaged_at          timestamptz NOT NULL,
    -- NULL while engaged, not yet married.
    married_at          timestamptz,
    CHECK (first_character_id <> second_character_id)
);

CREATE TABLE underground_coliseum_best_teams (
    arena_id            integer PRIMARY KEY,
    leader_character_id uuid    NOT NULL REFERENCES characters (id) ON DELETE CASCADE,
    wins                integer NOT NULL CHECK (wins >= 0)
);

CREATE INDEX underground_coliseum_best_teams_leader_idx ON underground_coliseum_best_teams (leader_character_id);

CREATE TABLE airships (
    clan_id uuid    PRIMARY KEY REFERENCES clans (id) ON DELETE CASCADE,
    fuel    integer NOT NULL CHECK (fuel >= 0)
);

-- Cycles: characters.clan_id and clan_hall_auction_bids.clan_id reference clans, so these are added after both exist.

-- No ON DELETE: a clan leader cannot be deleted (GameClient.markToDeleteChar refuses clan members).
ALTER TABLE clans
    ADD CONSTRAINT clans_leader_character_id_fkey
        FOREIGN KEY (leader_character_id) REFERENCES characters (id);

ALTER TABLE clans
    ADD CONSTRAINT clans_new_leader_character_id_fkey
        FOREIGN KEY (new_leader_character_id) REFERENCES characters (id) ON DELETE SET NULL;

ALTER TABLE clan_subpledges
    ADD CONSTRAINT clan_subpledges_leader_character_id_fkey
        FOREIGN KEY (leader_character_id) REFERENCES characters (id) ON DELETE SET NULL;

ALTER TABLE siegable_hall_flag_war_attacker_members
    ADD CONSTRAINT siegable_hall_flag_war_attacker_members_character_id_fkey
        FOREIGN KEY (character_id) REFERENCES characters (id) ON DELETE CASCADE;

-- Clearing only bid_clan_hall_id when the bid row goes (outbid, cancelled, auction ended).
ALTER TABLE clans
    ADD CONSTRAINT clans_bid_fkey
        FOREIGN KEY (bid_clan_hall_id, id) REFERENCES clan_hall_auction_bids (clan_hall_id, clan_id)
            ON DELETE SET NULL (bid_clan_hall_id);

--changeset game:000-seed
INSERT INTO seven_signs_status (id, current_cycle, festival_cycle, active_period) VALUES (0, 1, 1, 'COMPETITION');

INSERT INTO forums (name, forum_type, permission) VALUES
    ('NormalRoot', 'ROOT', 'ALL'),
    ('ClanRoot', 'ROOT', 'INVISIBLE'),
    ('MemoRoot', 'ROOT', 'INVISIBLE'),
    ('MailRoot', 'ROOT', 'INVISIBLE');

-- Hellbound starts fully open, as upstream ships it.
INSERT INTO global_variables (name, content) VALUES ('HBLevel', '11'), ('HBTrust', '4000000');

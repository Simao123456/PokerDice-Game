BEGIN;

INSERT INTO Invitation (code, is_active)
VALUES ('INV-ALICE-001', false),
       ('INV-BOB-001', false),
       ('INV-CAROL-001', true);

INSERT INTO Users (name, password, email, balance)
VALUES ('Alice', '$2a$10$alice-mock-hash', 'alice@example.com', 11.00),
       ('Bob', '$2a$10$bob-mock-hash', 'bob@example.com', 7.00),
       ('Carol', '$2a$10$carol-mock-hash', 'carol@example.com', 5.00),
       ('Dave', '$2a$10$dave-mock-hash', 'dave@example.com', 3.00);

INSERT INTO Session (session_id, user_id, created_at, last_used_at, expires_at, revoked)
VALUES ('11111111-1111-1111-1111-111111111111', 1, 1750000000000, 1750001000000, 1750600000000, false),
       ('22222222-2222-2222-2222-222222222222', 2, 1750000005000, 1750001200000, 1750600200000, false),
       ('33333333-3333-3333-3333-333333333333', 3, 1750000010000, 1750000300000, 1750600400000, true);

INSERT INTO Lobby (name, description, status, host_id, min_players, max_players, max_rounds, timeout_seconds,
                   created_at)
VALUES ('Casual Table', 'Jogo relaxado, 3 rondas', 'waiting', 1, 2, 4, 3, 60, 1750000500000),
       ('Ranked Table', 'Partida ranked, 5 rondas', 'started', 2, 2, 4, 5, 45, 1750000600000);

INSERT INTO Lobby_Users (lobby_id, user_id, joined_at)
VALUES (1, 1, 1750000505000),
       (1, 3, 1750000510000),
       (2, 2, 1750000605000),
       (2, 1, 1750000610000);

INSERT INTO Match (lobby_id, status, starting_player_user_id, current_round_id, created_at, finished_at)
VALUES (2, 'ongoing', 2, NULL, 1750000620000, NULL);

INSERT INTO Round (match_id, number, blind, pot, winner_user_id)
VALUES (1, 1, 1.00, 2.00, 2),
       (1, 2, 1.00, 2.00, NULL);

INSERT INTO Turn (round_id, user_id, state, number, roll_count)
VALUES (1, 2, 'ended', 1, 3),
       (1, 1, 'ended', 2, 2),
       (2, 1, 'active', 1, 1);

INSERT INTO Roll (turn_id, created_at, held_mask, dice_values)
VALUES (1, 1750000625000, '00000', 'Q,J,10,9,9'),
       (1, 1750000630000, '00011', 'K,J,10,9,9'),
       (1, 1750000635000, '10011', 'K,K,10,9,9');

INSERT INTO Roll (turn_id, created_at, held_mask, dice_values)
VALUES (2, 1750000640000, '00000', 'K,Q,10,9,9'),
       (2, 1750000645000, '11000', 'K,K,10,9,9');

INSERT INTO Roll (turn_id, created_at, held_mask, dice_values)
VALUES (3, 1750000650000, '00000', 'A,10,10,9,9');

INSERT INTO Hand (round_id, user_id, faces, rank, tie_breaker_key)
VALUES (1, 2, 'K,K,10,9,9', 3, 13),
       (1, 1, 'K,K,10,9,9', 3, 13);

COMMIT;
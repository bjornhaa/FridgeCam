-- Bare for lokal utvikling: testbruker med egen husstand, samme som i talik-ai.
-- BCrypt-hashen er for passordet 'qwer1234'.

WITH bruker AS (
    INSERT INTO bruker (epost, navn, passord_hash)
    VALUES ('bjorn@haakenstad.com', 'Bjørn', '$2y$10$uSiBgHNZKO1zX7rlPALdJeTaq6wTEZAAgj.INY2g9uDmh2U1Bo4u.')
    RETURNING id
), husstand AS (
    INSERT INTO husstand (navn) VALUES ('Hjemme')
    RETURNING id
)
INSERT INTO husstand_medlem (husstand_id, bruker_id, rolle)
SELECT husstand.id, bruker.id, 'EIER' FROM husstand, bruker;

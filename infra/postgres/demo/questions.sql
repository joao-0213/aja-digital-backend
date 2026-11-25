BEGIN;
SET LOCAL TIME ZONE 'America/Sao_Paulo';
SELECT pg_advisory_xact_lock(hashtext('aja-demo-seed-questions'));
CREATE TABLE IF NOT EXISTS questions.demo_seed_state(seed_key text PRIMARY KEY, seeded_at timestamptz NOT NULL DEFAULT now());
SELECT EXISTS(SELECT 1 FROM questions.demo_seed_state WHERE seed_key='aja-demo-v1') AS already_seeded \gset
\if :already_seeded
\echo 'Questions demo seed already applied; preserving existing data.'
\else
INSERT INTO question_bank(id,revision,created_at,subject,topic,difficulty,institution,year,statement,options,correct_option,explanation,image_file_id,image_alt) VALUES
('demo-question-1',1,now(),'Matemática','Proporções','easy','Acervo demonstrativo AJA',extract(year from current_date)::int,'Uma bebida usa 2 copos de água para 1 de concentrado. Quanto concentrado acompanha 6 copos de água?',ARRAY['2 copos','3 copos','4 copos','6 copos'],1,'A razão é 2 para 1; triplicando a água, triplique o concentrado.',NULL,NULL),
('demo-question-2',1,now(),'Matemática','Escalas','medium','Acervo demonstrativo AJA',extract(year from current_date)::int,'Num mapa, 2 cm representam 1 km. Quanto representam 5 cm?',ARRAY['1,5 km','2 km','2,5 km','5 km'],2,'Cada centímetro representa meio quilômetro.','demo-file-question','Desenho de grupos usados para comparar escalas'),
('demo-question-3',1,now(),'Matemática','Porcentagens','easy','Acervo demonstrativo AJA',extract(year from current_date)::int,'Uma mochila custa R$ 80 e recebe 25% de desconto. Qual é o novo preço?',ARRAY['R$ 20','R$ 55','R$ 60','R$ 75'],2,'25% de 80 é 20; 80 menos 20 é 60.',NULL,NULL);
INSERT INTO questions.demo_seed_state(seed_key) VALUES ('aja-demo-v1');
\endif
COMMIT;

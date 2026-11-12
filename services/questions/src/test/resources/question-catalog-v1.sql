DELETE FROM questions.question_bank;

INSERT INTO questions.question_bank
    (id, revision, created_at, subject, topic, difficulty, institution, year, statement, options, correct_option, explanation, image_file_id, image_alt, withdrawn_at)
VALUES
    ('q1', 1, '2025-01-01T12:00:00Z', 'Matemática', 'Álgebra', 'easy', 'AJA', 1, 'Resolva 2x + 3 = 7.', ARRAY['x = 1', 'x = 2'], 1, 'Subtraia 3 e divida por 2.', NULL, NULL, NULL),
    ('q2', 1, '2025-01-02T12:00:00Z', 'Matemática', 'Geometria', 'easy', 'AJA', 1, 'Qual é o perímetro do quadrado?', ARRAY['4 cm', '16 cm'], 0, 'O perímetro soma os lados.', NULL, NULL, NULL),
    ('q-image', 2, '2025-01-03T12:00:00Z', 'Ciências', 'Ecologia', 'medium', 'AJA', 2, 'Observe o esquema.', ARRAY['Opção A', 'Opção B'], 1, 'A opção B corresponde ao esquema.', 'file-question-image-v1', 'Esquema de ecologia', NULL),
    ('q-withdrawn', 1, '2025-01-04T12:00:00Z', 'Matemática', 'Álgebra', 'hard', 'AJA', 3, 'Questão retirada.', ARRAY['Opção A', 'Opção B'], 0, 'Resolução retirada.', NULL, NULL, '2025-02-01T00:00:00Z');

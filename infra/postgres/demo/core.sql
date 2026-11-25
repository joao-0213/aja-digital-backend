BEGIN;
SET LOCAL TIME ZONE 'America/Sao_Paulo';
SELECT pg_advisory_xact_lock(hashtext('aja-demo-seed-core'));
CREATE TABLE IF NOT EXISTS core.demo_seed_state(seed_key text PRIMARY KEY, seeded_at timestamptz NOT NULL DEFAULT now());
SELECT EXISTS(SELECT 1 FROM core.demo_seed_state WHERE seed_key='aja-demo-v1') AS already_seeded \gset
\if :already_seeded
\echo 'Core demo seed already applied; preserving existing data.'
\else

INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at) VALUES
('demo-admin',1,now()-interval '30 days',now()-interval '2 days','demo-admin','Ana Administração','admin@aja.local.test','admin@aja.local.test','$argon2id$v=19$m=16384,t=2,p=1$bSxL011Gzw7FgUPKayTZfA$97u/Rf7zyF7lWMDuN8hr1oP+wNbXXSnrtn0YtcSOLTA','admin',true,now()),
('demo-teacher',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','Daniela Professora','professora@aja.local.test','professora@aja.local.test','$argon2id$v=19$m=16384,t=2,p=1$bSxL011Gzw7FgUPKayTZfA$97u/Rf7zyF7lWMDuN8hr1oP+wNbXXSnrtn0YtcSOLTA','teacher',true,now()),
('demo-student',1,now()-interval '30 days',now()-interval '2 days','demo-admin','Alex Aluno','aluno@aja.local.test','aluno@aja.local.test','$argon2id$v=19$m=16384,t=2,p=1$bSxL011Gzw7FgUPKayTZfA$97u/Rf7zyF7lWMDuN8hr1oP+wNbXXSnrtn0YtcSOLTA','student',true,now()),
('demo-student-2',1,now()-interval '30 days',now()-interval '2 days','demo-admin','Beatriz Aluna','bia@aja.local.test','bia@aja.local.test','$argon2id$v=19$m=16384,t=2,p=1$bSxL011Gzw7FgUPKayTZfA$97u/Rf7zyF7lWMDuN8hr1oP+wNbXXSnrtn0YtcSOLTA','student',true,now());

INSERT INTO core.classes(id,revision,created_at,updated_at,updated_by,name,code,normalized_code,year,shift,active) VALUES ('demo-class',1,now()-interval '30 days',now()-interval '2 days','demo-admin','Horizonte - turma interdisciplinar','HORIZONTE-DEMO','HORIZONTE-DEMO',extract(year from current_date)::int,'Noite',true);
INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by,enrolled_at) VALUES
('demo-enrollment-1',1,now()-interval '30 days',now()-interval '2 days','demo-admin','demo-class','aluno@aja.local.test','aluno@aja.local.test','demo-student','active','demo-admin',now()-interval '30 days'),
('demo-enrollment-2',1,now()-interval '30 days',now()-interval '2 days','demo-admin','demo-class','bia@aja.local.test','bia@aja.local.test','demo-student-2','active','demo-admin',now()-interval '30 days');
INSERT INTO core.subjects(id,revision,created_at,updated_at,updated_by,class_id,name,color,icon,description) VALUES ('demo-subject',1,now()-interval '30 days',now()-interval '2 days','demo-admin','demo-class','Matemática e Proporções','blue','calculator','Uma jornada visual por razões, escalas e porcentagens.');
INSERT INTO core.subject_teachers(subject_id,teacher_id) VALUES ('demo-subject','demo-teacher');
INSERT INTO core.modules(id,revision,created_at,updated_at,updated_by,subject_id,title,description,order_index) VALUES
('demo-module-1',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-subject','Razões no cotidiano','Receitas, mapas e proporções em situações reais.',0),
('demo-module-2',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-subject','Escalas e mapas','Aplique razões para ler distâncias e representações.',1);
INSERT INTO core.files(id,owner_id,purpose,parent_id,name,mime,size,sha256,status,expires_at,content,created_at) VALUES
('demo-file-pdf','demo-teacher','material','demo-material-pdf','proporcoes.pdf','application/pdf',__DEMO_PDF_SIZE__,'__DEMO_PDF_SHA256__','attached',NULL,decode('__DEMO_PDF_BASE64__','base64'),now()-interval '30 days'),
('demo-file-question',NULL,'question','demo-question-2','grupos.png','image/png',__DEMO_PNG_SIZE__,'__DEMO_PNG_SHA256__','attached',NULL,decode('__DEMO_PNG_BASE64__','base64'),now()-interval '30 days');
INSERT INTO core.materials(id,revision,created_at,updated_at,updated_by,author_id,module_id,title,kind,duration,content,file_id,video_url,order_index) VALUES
('demo-material-1',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-teacher','demo-module-1','Uma receita para compartilhar','lesson',8,'Se uma receita serve 2 pessoas, dobre cada ingrediente para servir 4. A razão permanece constante.',NULL,NULL,0),
('demo-material-2',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-teacher','demo-module-1','Desafio: a escala do bairro','lesson',12,'Em um mapa, 1 cm representa 500 m. Que distância real representa um trajeto de 3,5 cm?',NULL,NULL,1),
('demo-material-3',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-teacher','demo-module-2','Proporção em imagens','video',6,'Observe como escalas preservam relações.',NULL,'https://www.youtube.com/watch?v=aqz-KE-bpKQ',0),('demo-material-pdf',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-teacher','demo-module-1','Guia visual de proporções','pdf',10,'','demo-file-pdf',NULL,2);
INSERT INTO core.material_progress(id,revision,created_at,updated_at,updated_by,user_id,material_id,completed,last_access) VALUES
('demo-progress-1',1,now()-interval '30 days',now()-interval '2 days','demo-student','demo-student','demo-material-1',true,now()-interval '2 days'),
('demo-progress-2',1,now()-interval '30 days',now()-interval '2 days','demo-student','demo-student','demo-material-2',false,now()-interval '1 day');

INSERT INTO core.assessments(id,module_id,kind,status,revision,published_at,deadline,allow_late,created_at,updated_at,updated_by,title,description,max_grade,required,order_index) VALUES
('demo-quiz', 'demo-module-1','quiz','published',1,now()-interval '7 days',now()+interval '5 days',false,now()-interval '8 days',now(),'demo-teacher','Missão proporções','Resolva os desafios e explique seu raciocínio.',10,true,0),
('demo-work','demo-module-1','work','published',1,now()-interval '4 days',now()+interval '10 days',true,now()-interval '5 days',now(),'demo-teacher','Projeto: feira sustentável','Monte uma proposta de compras para uma feira com orçamento proporcional.',10,true,1),
('demo-draft','demo-module-2','quiz','draft',1,NULL,NULL,false,now()-interval '30 days',now()-interval '2 days','demo-teacher','Rascunho - escalas','Atividade em preparação.',10,false,0),('demo-quiz-next','demo-module-1','quiz','published',1,now(),now()+interval '12 days',false,now()-interval '1 day',now(),'demo-teacher','Desafio de revisão','Atividade aberta para planejar os estudos.',10,true,2);
INSERT INTO core.assessment_questions(assessment_id,question_id,position,source,source_revision,content_hash,snapshot) VALUES
('demo-quiz','demo-authored-1',0,'author',NULL,NULL,'{"id":"demo-authored-1","origin":"author","subject":"Matemática","topic":"Proporções","difficulty":"easy","institution":"Acervo demonstrativo AJA","year":2026,"image":null,"statement":"Uma bebida usa 2 copos de água para 1 de concentrado. Quanto para 6 copos de água?","options":["2","3","4","6"],"correct":1,"explanation":"Mantenha a razão 2 para 1."}'),
('demo-quiz','demo-authored-2',1,'author',NULL,NULL,'{"id":"demo-authored-2","origin":"author","subject":"Matemática","topic":"Escalas","difficulty":"medium","institution":"Acervo demonstrativo AJA","year":2026,"image":null,"statement":"Num mapa, 2 cm representam 1 km. Quanto representam 5 cm?","options":["1,5 km","2 km","2,5 km","5 km"],"correct":2,"explanation":"Cada centímetro representa meio quilômetro."}');
INSERT INTO core.assessment_questions(assessment_id,question_id,position,source,source_revision,content_hash,snapshot) SELECT 'demo-quiz-next',question_id,0,source,source_revision,content_hash,snapshot FROM core.assessment_questions WHERE assessment_id='demo-quiz' AND question_id='demo-authored-1';
INSERT INTO core.assessment_questions(assessment_id,question_id,position,source,source_revision,content_hash,snapshot) SELECT 'demo-draft',question_id,0,source,source_revision,content_hash,snapshot FROM core.assessment_questions WHERE assessment_id='demo-quiz' AND question_id='demo-authored-1';
INSERT INTO core.attempts(id,assessment_id,user_id,revision,created_at,updated_at,updated_by,started_at,submitted_at,answers,question_order,questions,receipt_id,score,max_score) VALUES
('demo-attempt', 'demo-quiz','demo-student',1,now()-interval '1 day',now()-interval '1 day','demo-student',now()-interval '1 day',now()-interval '1 day','{"demo-authored-1":1,"demo-authored-2":2}','["demo-authored-1","demo-authored-2"]','[{"id":"demo-authored-1","origin":"author","subject":"Matemática","topic":"Proporções","difficulty":"easy","institution":"Acervo demonstrativo AJA","year":2026,"image":null,"statement":"Uma bebida usa 2 copos de água para 1 de concentrado. Quanto para 6 copos de água?","options":["2","3","4","6"],"correct":1,"explanation":"Mantenha a razão 2 para 1."},{"id":"demo-authored-2","origin":"author","subject":"Matemática","topic":"Escalas","difficulty":"medium","institution":"Acervo demonstrativo AJA","year":2026,"image":null,"statement":"Num mapa, 2 cm representam 1 km. Quanto representam 5 cm?","options":["1,5 km","2 km","2,5 km","5 km"],"correct":2,"explanation":"Cada centímetro representa meio quilômetro."}]','demo-receipt',10,10);
INSERT INTO core.submissions(id,revision,created_at,updated_at,updated_by,assessment_id,user_id,attachments,submitted_at,receipt_id,feedback_published,feedback_history) VALUES
('demo-submission-pending-feedback',1,now()-interval '2 days',now()-interval '1 day','demo-student','demo-work','demo-student','[{"kind":"link","name":"Proposta da feira","url":"https://example.org/aja-demo-alex"}]',now()-interval '2 days','demo-work-receipt-alex',NULL,'[]'),
('demo-submission',1,now()-interval '2 days',now()-interval '1 day','demo-teacher','demo-work','demo-student-2','[{"kind":"link","name":"Planilha da feira","url":"https://example.org/aja-demo"}]',now()-interval '2 days','demo-work-receipt',jsonb_build_object('comment','Boa proposta! Confira a unidade antes de apresentar.','grade',9,'author',jsonb_build_object('id','demo-teacher','name','Daniela Professora'),'createdAt',now()-interval '1 day'),jsonb_build_array(jsonb_build_object('comment','Boa proposta! Confira a unidade antes de apresentar.','grade',9,'author',jsonb_build_object('id','demo-teacher','name','Daniela Professora'),'createdAt',now()-interval '1 day')));
INSERT INTO core.announcements(id,revision,created_at,updated_at,updated_by,created_by,class_id,subject_id,title,body,notify,occurrence) VALUES
('demo-announcement-1',1,now()-interval '1 day',now(),'demo-teacher','demo-teacher','demo-class','demo-subject','Feira de ideias nesta sexta','Traga uma situação do dia a dia em que você usa proporções.',true,jsonb_build_object('date',current_date+3,'time','19:00','allDay',false)),
('demo-announcement-2',1,now()-interval '30 days',now()-interval '2 days','demo-admin','demo-admin','demo-class',NULL,'Semana de projetos','Compartilhe suas descobertas no fórum da turma.',false,jsonb_build_object('date',current_date+6,'allDay',true));
INSERT INTO core.attendance(id,revision,created_at,updated_at,updated_by,subject_id,class_date,class_time,records) VALUES
('demo-attendance-1',1,now()-interval '2 days',now(),'demo-teacher','demo-subject',current_date-2,'19:00','{"demo-student":true,"demo-student-2":false}'),
('demo-attendance-2',1,now()-interval '9 days',now(),'demo-teacher','demo-subject',current_date-9,'19:00','{"demo-student":true,"demo-student-2":true}');
INSERT INTO core.topics(id,revision,created_at,updated_at,last_activity_at,updated_by,author_id,class_id,subject_id,title,body,tags,target_type,target_id) VALUES
('demo-topic',1,now()-interval '2 days',now()-interval '30 days',now()-interval '2 days','demo-student','demo-student','demo-class','demo-subject','Como adaptar a receita para a turma?','Encontrei uma receita para 4 pessoas. Como ajusto as quantidades para 10 sem perder o sabor?','["proporção","receitas"]',NULL,NULL),
('demo-topic-2',1,now()-interval '1 day',now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-teacher','demo-class','demo-subject','Compartilhe um exemplo de escala','Onde você encontra escalas no seu bairro?','["mapas"]',NULL,NULL);
INSERT INTO core.topic_replies(id,topic_id,revision,created_at,updated_at,updated_by,author_id,body) VALUES
('demo-reply', 'demo-topic',1,now()-interval '1 day',now(),'demo-teacher','demo-teacher','Multiplique cada ingrediente por 2,5: assim a razão entre eles se mantém.');
INSERT INTO core.topic_reads(topic_id,user_id,read_at) VALUES ('demo-topic','demo-student',now());
INSERT INTO core.topic_votes(reply_id,user_id,created_at) VALUES ('demo-reply','demo-student',now());
INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at) VALUES
('demo-student-3',1,now()-interval '30 days',now()-interval '2 days','demo-admin','Caio Curioso','caio@aja.local.test','caio@aja.local.test','$argon2id$v=19$m=16384,t=2,p=1$bSxL011Gzw7FgUPKayTZfA$97u/Rf7zyF7lWMDuN8hr1oP+wNbXXSnrtn0YtcSOLTA','student',true,now()),
('demo-pending',1,now()-interval '30 days',now()-interval '2 days','demo-admin','Dora Nova','dora@aja.local.test','dora@aja.local.test','$argon2id$v=19$m=16384,t=2,p=1$bSxL011Gzw7FgUPKayTZfA$97u/Rf7zyF7lWMDuN8hr1oP+wNbXXSnrtn0YtcSOLTA','student',true,NULL),
('demo-inactive',1,now()-interval '30 days',now()-interval '2 days','demo-admin','Eva Inativa','eva@aja.local.test','eva@aja.local.test','$argon2id$v=19$m=16384,t=2,p=1$bSxL011Gzw7FgUPKayTZfA$97u/Rf7zyF7lWMDuN8hr1oP+wNbXXSnrtn0YtcSOLTA','student',false,now());
INSERT INTO core.classes(id,revision,created_at,updated_at,updated_by,name,code,normalized_code,year,shift,active) VALUES
('demo-class-2',1,now()-interval '30 days',now()-interval '2 days','demo-admin','Ateliê de Ciências','ATELIE-DEMO','ATELIE-DEMO',extract(year from current_date)::int,'Tarde',true),
('demo-class-old',1,now()-interval '30 days',now()-interval '2 days','demo-admin','Turma arquivo 2024','ARQUIVO-DEMO','ARQUIVO-DEMO',extract(year from current_date)::int-2,'Manhã',false);
INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by,enrolled_at) VALUES
('demo-enrollment-3',1,now()-interval '30 days',now()-interval '2 days','demo-admin','demo-class-2','caio@aja.local.test','caio@aja.local.test','demo-student-3','active','demo-admin',now()-interval '30 days'),
('demo-enrollment-pending',1,now()-interval '30 days',now()-interval '2 days','demo-admin','demo-class','dora@aja.local.test','dora@aja.local.test',NULL,'pending','demo-admin',NULL);
INSERT INTO core.subjects(id,revision,created_at,updated_at,updated_by,class_id,name,color,icon,description) VALUES
('demo-subject-2',1,now()-interval '30 days',now()-interval '2 days','demo-admin','demo-class','Ciências em Contexto','green','atom','Investigações práticas, dados e leitura de gráficos.'),
('demo-subject-old',1,now()-interval '30 days',now()-interval '2 days','demo-admin','demo-class-old','Matemática - acervo histórico','amber','book','Conteúdo preservado para consulta administrativa.');
INSERT INTO core.subject_teachers(subject_id,teacher_id) VALUES ('demo-subject-2','demo-teacher'),('demo-subject-old','demo-teacher');
INSERT INTO core.modules(id,revision,created_at,updated_at,updated_by,subject_id,title,description,order_index) VALUES
('demo-module-3',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-subject-2','Dados do jardim escolar','Observe padrões, compare medidas e formule hipóteses.',0),
('demo-module-old',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-subject-old','Registro anterior','Material de referência da turma arquivada.',0);
INSERT INTO core.materials(id,revision,created_at,updated_at,updated_by,author_id,module_id,title,kind,duration,content,video_url,order_index) VALUES
('demo-material-4',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-teacher','demo-module-3','Diário de observação','lesson',7,'Registre medidas do jardim e compare-as ao longo da semana.',NULL,0),
('demo-material-old',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-teacher','demo-module-old','Caderno de campo arquivado','lesson',5,'Notas de estudo preservadas para consulta administrativa.',NULL,0);
INSERT INTO core.announcements(id,revision,created_at,updated_at,updated_by,created_by,class_id,subject_id,title,body,notify,occurrence) VALUES
('demo-announcement-3',1,now()-interval '30 days',now()-interval '2 days','demo-teacher','demo-teacher','demo-class','demo-subject-2','Mostra de descobertas','Traga uma observação do seu entorno para a aula.',true,jsonb_build_object('date',current_date+4,'time','15:00','allDay',false));
INSERT INTO core.attendance(id,revision,created_at,updated_at,updated_by,subject_id,class_date,class_time,records) VALUES
('demo-attendance-3',1,now()-interval '16 days',now(),'demo-teacher','demo-subject',current_date-16,'19:00','{"demo-student":false,"demo-student-2":true}');
INSERT INTO core.demo_seed_state(seed_key) VALUES ('aja-demo-v1');
\endif
COMMIT;

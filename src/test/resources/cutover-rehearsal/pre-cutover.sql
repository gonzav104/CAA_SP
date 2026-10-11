-- Synthetic data only. No production dump or active database connection.
BEGIN;
INSERT INTO usuarios (id,email,password_hash,nombre,rol) VALUES
('00000000-0000-0000-0000-000000000001','legacy@rehearsal.test','hash','Legacy','TERAPEUTA'),
('00000000-0000-0000-0000-000000000002','manager@rehearsal.test','hash','Manager',NULL),
('00000000-0000-0000-0000-000000000003','clinical@rehearsal.test','hash','Clinical','TERAPEUTA'),
('00000000-0000-0000-0000-000000000004','idle@rehearsal.test','hash','Idle','TERAPEUTA'),
('00000000-0000-0000-0000-000000000005','family@rehearsal.test','hash','Family','FAMILIAR');
INSERT INTO organizaciones (id,nombre,creado_por_id) VALUES
('10000000-0000-0000-0000-000000000002','Modern workspace','00000000-0000-0000-0000-000000000002');
INSERT INTO membresias (organizacion_id,usuario_id,rol_gestion,es_terapeuta) VALUES
('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002','OWNER',false),
('10000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','MIEMBRO',true);
INSERT INTO pacientes (id,terapeuta_id,nombre,apellido,fecha_nacimiento,organizacion_id) VALUES
('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','Late legacy','Patient','2015-01-01',NULL),
('20000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000002','Management unassigned','Patient','2015-01-01','10000000-0000-0000-0000-000000000002'),
('20000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000003','Clinical assigned','Patient','2015-01-01','10000000-0000-0000-0000-000000000002'),
('20000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000003','Deliberately unassigned','Patient','2015-01-01','10000000-0000-0000-0000-000000000002');
INSERT INTO pacientes_terapeutas (paciente_id,usuario_id,organizacion_id) VALUES
('20000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000002');
INSERT INTO pacientes_familiares (paciente_id,usuario_id,permiso) VALUES
('20000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000005','LECTURA');
INSERT INTO sesiones (paciente_id,fecha_hora,objetivos_trabajados) VALUES
('20000000-0000-0000-0000-000000000001','2026-01-01 12:00:00','Preserve clinical history');
COMMIT;

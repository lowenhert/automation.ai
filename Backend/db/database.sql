use reportingdb;
CREATE TABLE stb_inventory (
                               id INT AUTO_INCREMENT PRIMARY KEY,
                               uuid CHAR(36) NOT NULL,
                               vendor VARCHAR(50) NOT NULL,
                               model VARCHAR(50) NOT NULL,
                               resolution ENUM('HD','SD') NOT NULL,
                               stb_no INT NOT NULL,
                               UNIQUE KEY uk_uuid (uuid)
);

INSERT INTO stb_inventory (uuid, vendor, model, resolution, stb_no) VALUES
                                                                        (UUID(),'SYNAMEDIA','IVY','HD',45),
                                                                        (UUID(),'SYNAMEDIA','Phoenix-1','HD',42),
                                                                        (UUID(),'SYNAMEDIA','Phoenix-1','HD',43),
                                                                        (UUID(),'SYNAMEDIA','Phoenix-3','HD',44),
                                                                        (UUID(),'SYNAMEDIA','Samsung','HD',74),
                                                                        (UUID(),'SYNAMEDIA','Samsung','HD',75),
                                                                        (UUID(),'SYNAMEDIA','Leo_PVR','HD',62),
                                                                        (UUID(),'SYNAMEDIA','NOVA','HD',86),
                                                                        (UUID(),'SYNAMEDIA','Jade','HD',77),
                                                                        (UUID(),'SYNAMEDIA','Aslan_PVR','HD',61),
                                                                        (UUID(),'SYNAMEDIA','T-51','HD',27),
                                                                        (UUID(),'SYNAMEDIA','T-52','HD',28),
                                                                        (UUID(),'SYNAMEDIA','Kaon','HD',65),
                                                                        (UUID(),'SYNAMEDIA','Miracle','HD',53),
                                                                        (UUID(),'SYNAMEDIA','Panna','HD',87),
                                                                        (UUID(),'SYNAMEDIA','Panna','HD',88),
                                                                        (UUID(),'SYNAMEDIA','Jupiter','HD',84),
                                                                        (UUID(),'SYNAMEDIA','Jupiter','HD',85),
                                                                        (UUID(),'SYNAMEDIA','T-41','SD',41),
                                                                        (UUID(),'SYNAMEDIA','Sapphire','SD',13),
                                                                        (UUID(),'SYNAMEDIA','Neptune','SD',98),
                                                                        (UUID(),'SYNAMEDIA','Mars-1','SD',94),
                                                                        (UUID(),'SYNAMEDIA','Mars-2','SD',95),
                                                                        (UUID(),'SYNAMEDIA','Heera','SD',16);


INSERT INTO stb_inventory (uuid, vendor, model, resolution, stb_no) VALUES
                                                                        (UUID(),'IRDETO','Nysa','HD',34),
                                                                        (UUID(),'IRDETO','Navya','HD',30),
                                                                        (UUID(),'IRDETO','Navya-2','HD',31),
                                                                        (UUID(),'IRDETO','Navya-2','HD',32),
                                                                        (UUID(),'IRDETO','Navya-3','HD',33),
                                                                        (UUID(),'IRDETO','Prithivi-1','HD',38),
                                                                        (UUID(),'IRDETO','Prithivi-2','HD',39),
                                                                        (UUID(),'IRDETO','Prithivi-4','HD',35);

CREATE INDEX idx_vendor_model
    ON stb_inventory(vendor, model);

CREATE INDEX idx_resolution
    ON stb_inventory(resolution);



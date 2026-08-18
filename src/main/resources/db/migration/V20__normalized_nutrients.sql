CREATE TABLE nutrient_definition (
    code VARCHAR(80) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    nutrient_group VARCHAR(40) NOT NULL,
    unit VARCHAR(12) NOT NULL,
    display_order INTEGER NOT NULL,
    visible BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE food_nutrient (
    id BIGSERIAL PRIMARY KEY,
    food_id BIGINT NOT NULL REFERENCES food(id) ON DELETE CASCADE,
    nutrient_code VARCHAR(80) NOT NULL REFERENCES nutrient_definition(code),
    nutrient_value NUMERIC(38, 6),
    source VARCHAR(40) NOT NULL DEFAULT 'LEGACY',
    status VARCHAR(20) NOT NULL DEFAULT 'PARTIAL',
    external_reference VARCHAR(160),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(food_id, nutrient_code)
);

CREATE TABLE food_log_nutrient (
    id BIGSERIAL PRIMARY KEY,
    food_log_id BIGINT NOT NULL REFERENCES food_log(id) ON DELETE CASCADE,
    nutrient_code VARCHAR(80) NOT NULL REFERENCES nutrient_definition(code),
    nutrient_value NUMERIC(38, 6),
    source VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    UNIQUE(food_log_id, nutrient_code)
);

INSERT INTO nutrient_definition(code, name, nutrient_group, unit, display_order) VALUES
 ('CALORIES','Energía','MACRO','kcal',10),
 ('PROTEIN','Proteínas','MACRO','g',20),
 ('CARBOHYDRATE','Carbohidratos','MACRO','g',30),
 ('FAT','Grasas','MACRO','g',40),
 ('FIBER','Fibra dietaria','CARBOHYDRATE','g',110),
 ('SUGARS_TOTAL','Azúcares totales','CARBOHYDRATE','g',120),
 ('SUGARS_ADDED','Azúcares agregados','CARBOHYDRATE','g',130),
 ('STARCH','Almidón','CARBOHYDRATE','g',140),
 ('SATURATED_FAT','Grasas saturadas','FAT','g',210),
 ('TRANS_FAT','Grasas trans','FAT','g',220),
 ('MONOUNSATURATED_FAT','Grasas monoinsaturadas','FAT','g',230),
 ('POLYUNSATURATED_FAT','Grasas poliinsaturadas','FAT','g',240),
 ('CHOLESTEROL','Colesterol','FAT','mg',250),
 ('SODIUM','Sodio','MINERAL','mg',310),
 ('POTASSIUM','Potasio','MINERAL','mg',320),
 ('CALCIUM','Calcio','MINERAL','mg',330),
 ('IRON','Hierro','MINERAL','mg',340),
 ('MAGNESIUM','Magnesio','MINERAL','mg',350),
 ('PHOSPHORUS','Fósforo','MINERAL','mg',360),
 ('ZINC','Zinc','MINERAL','mg',370),
 ('COPPER','Cobre','MINERAL','mg',380),
 ('MANGANESE','Manganeso','MINERAL','mg',390),
 ('SELENIUM','Selenio','MINERAL','µg',400),
 ('VITAMIN_A','Vitamina A','VITAMIN','µg',510),
 ('VITAMIN_C','Vitamina C','VITAMIN','mg',520),
 ('VITAMIN_D','Vitamina D','VITAMIN','µg',530),
 ('VITAMIN_E','Vitamina E','VITAMIN','mg',540),
 ('VITAMIN_K','Vitamina K','VITAMIN','µg',550),
 ('VITAMIN_B1','Tiamina (B1)','VITAMIN','mg',560),
 ('VITAMIN_B2','Riboflavina (B2)','VITAMIN','mg',570),
 ('VITAMIN_B3','Niacina (B3)','VITAMIN','mg',580),
 ('VITAMIN_B5','Ácido pantoténico (B5)','VITAMIN','mg',590),
 ('VITAMIN_B6','Vitamina B6','VITAMIN','mg',600),
 ('VITAMIN_B7','Biotina (B7)','VITAMIN','µg',610),
 ('VITAMIN_B9','Folato (B9)','VITAMIN','µg',620),
 ('VITAMIN_B12','Vitamina B12','VITAMIN','µg',630);

INSERT INTO food_nutrient(food_id, nutrient_code, nutrient_value, source, status)
SELECT id, 'PROTEIN', protein_grams, 'LEGACY', 'PARTIAL' FROM food WHERE protein_grams IS NOT NULL;
INSERT INTO food_nutrient(food_id, nutrient_code, nutrient_value, source, status)
SELECT id, 'CARBOHYDRATE', carbs_grams, 'LEGACY', 'PARTIAL' FROM food WHERE carbs_grams IS NOT NULL;
INSERT INTO food_nutrient(food_id, nutrient_code, nutrient_value, source, status)
SELECT id, 'FAT', fat_grams, 'LEGACY', 'PARTIAL' FROM food WHERE fat_grams IS NOT NULL;
INSERT INTO food_nutrient(food_id, nutrient_code, nutrient_value, source, status)
SELECT id, 'CALORIES', calories, 'LEGACY', 'PARTIAL' FROM food WHERE calories IS NOT NULL;

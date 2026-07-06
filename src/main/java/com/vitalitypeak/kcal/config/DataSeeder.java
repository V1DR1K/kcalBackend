package com.vitalitypeak.kcal.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.vitalitypeak.kcal.catalog.Food;
import com.vitalitypeak.kcal.catalog.FoodCategory;
import com.vitalitypeak.kcal.catalog.FoodPreparation;
import com.vitalitypeak.kcal.catalog.FoodRepository;
import com.vitalitypeak.kcal.catalog.FoodUnit;
import com.vitalitypeak.kcal.nutrition.FoodLog;
import com.vitalitypeak.kcal.nutrition.FoodLogRepository;
import com.vitalitypeak.kcal.nutrition.MealType;
import com.vitalitypeak.kcal.nutrition.WaterLog;
import com.vitalitypeak.kcal.nutrition.WaterLogRepository;
import com.vitalitypeak.kcal.profile.NutritionPlan;
import com.vitalitypeak.kcal.profile.NutritionPlanRepository;
import com.vitalitypeak.kcal.user.ActivityLevel;
import com.vitalitypeak.kcal.user.AppUser;
import com.vitalitypeak.kcal.user.FitnessGoal;
import com.vitalitypeak.kcal.user.Gender;
import com.vitalitypeak.kcal.user.Role;
import com.vitalitypeak.kcal.user.UserRepository;

@Configuration
public class DataSeeder {
    @Bean
    CommandLineRunner seed(UserRepository users, FoodRepository foods, FoodLogRepository foodLogs,
            WaterLogRepository waterLogs, NutritionPlanRepository nutritionPlans, PasswordEncoder passwordEncoder,
            @Value("${app.seed.catalog-enabled:true}") boolean catalogEnabled,
            @Value("${app.seed.demo-users-enabled:true}") boolean demoUsersEnabled) {
        return args -> {
            if (catalogEnabled) {
            ensureFood(foods, "Pechuga de Pollo", "KazaFitness Premium Select", "7790000000011", FoodCategory.PROTEIN,
                    165, 31, 0, 3.6, FoodPreparation.COOKED, "USDA FDC 171477", Set.of("Alta en Proteina", "Keto Friendly"));
            ensureFood(foods, "Pechuga de Pollo", "KazaFitness Premium Select", "7790000000073", FoodCategory.PROTEIN,
                    120, 22.5, 0, 2.6, FoodPreparation.RAW, "USDA FDC 171077", Set.of("Alta en Proteina", "Keto Friendly"));
            ensureFood(foods, "Arroz Blanco", "Generico", "7790000000028", FoodCategory.CEREAL,
                    130, 2.7, 28, 0.3, FoodPreparation.COOKED, "USDA FDC 168878", Set.of("Carbohidrato"));
            ensureFood(foods, "Palta (Aguacate)", "Fresco", "7790000000035", FoodCategory.FAT,
                    160, 2, 8.5, 14.7, FoodPreparation.RAW, "USDA FDC 171705", Set.of("Grasas Saludables"));
            ensureFood(foods, "Yogur Griego Natural", "KazaFitness Dairy", "7790000000042", FoodCategory.DAIRY,
                    59, 10, 3.6, 0.4, FoodPreparation.AS_SOLD, "USDA FDC 330137", Set.of("Proteina"));
            ensureFood(foods, "Atun en lata", "Mar Azul", "7790000000059", FoodCategory.PROTEIN,
                    116, 26, 0, 1, FoodPreparation.AS_SOLD, "USDA FDC 334194", Set.of("Alta Proteina", "Keto"));
            ensureFood(foods, "Banana", "Fresco", "7790000000066", FoodCategory.FRUIT,
                    89, 1.1, 22.8, 0.3, FoodPreparation.RAW, "USDA FDC 173944", Set.of("Fruta"));
            ensureFood(foods, "Manzana", "Fresco", "7790000000103", FoodCategory.FRUIT,
                    64, 0.3, 14.9, 0.4, FoodPreparation.RAW, "ARGENFOODS 138", Set.of("Fruta", "Argentina"));
            ensureFood(foods, "Pera", "Fresco", "7790000000110", FoodCategory.FRUIT,
                    70, 0.7, 15.8, 0.4, FoodPreparation.RAW, "ARGENFOODS 144", Set.of("Fruta", "Argentina"));
            ensureFood(foods, "Durazno", "Fresco", "7790000000127", FoodCategory.FRUIT,
                    51, 0.5, 12, 0.1, FoodPreparation.RAW, "ARGENFOODS 129", Set.of("Fruta", "Argentina"));
            ensureFood(foods, "Frutilla", "Fresco", "7790000000134", FoodCategory.FRUIT,
                    41, 0.8, 8.1, 0.6, FoodPreparation.RAW, "ARGENFOODS 132", Set.of("Fruta", "Argentina"));
            ensureFood(foods, "Sandía", "Fresco", "7790000000141", FoodCategory.FRUIT,
                    31, 0.5, 6.9, 0.2, FoodPreparation.RAW, "ARGENFOODS 147", Set.of("Fruta", "Argentina"));
            ensureFood(foods, "Naranja", "Fresco", "7790000000142", FoodCategory.FRUIT,
                    47, 0.9, 11.8, 0.1, FoodPreparation.RAW, "USDA FDC 169097", Set.of("Fruta", "Vitamina C"));
            ensureFood(foods, "Mandarina", "Fresco", "7790000000143", FoodCategory.FRUIT,
                    53, 0.8, 13.3, 0.3, FoodPreparation.RAW, "USDA FDC 169103", Set.of("Fruta", "Vitamina C"));
            ensureFood(foods, "Uva", "Fresco", "7790000000144", FoodCategory.FRUIT,
                    69, 0.7, 18.1, 0.2, FoodPreparation.RAW, "USDA FDC 174682", Set.of("Fruta"));
            ensureFood(foods, "Kiwi", "Fresco", "7790000000145", FoodCategory.FRUIT,
                    61, 1.1, 14.7, 0.5, FoodPreparation.RAW, "USDA FDC 168150", Set.of("Fruta", "Vitamina C"));
            ensureFood(foods, "Ananá", "Fresco", "7790000000146", FoodCategory.FRUIT,
                    50, 0.5, 13.1, 0.1, FoodPreparation.RAW, "USDA FDC 169124", Set.of("Fruta"));
            ensureFood(foods, "Melón", "Fresco", "7790000000147", FoodCategory.FRUIT,
                    34, 0.8, 8.2, 0.2, FoodPreparation.RAW, "USDA FDC 169092", Set.of("Fruta"));
            ensureFood(foods, "Ciruela", "Fresco", "7790000000148", FoodCategory.FRUIT,
                    46, 0.7, 11.4, 0.3, FoodPreparation.RAW, "USDA FDC 169944", Set.of("Fruta"));
            ensureFood(foods, "Arándanos", "Fresco", "7790000000149", FoodCategory.FRUIT,
                    57, 0.7, 14.5, 0.3, FoodPreparation.RAW, "USDA FDC 171726", Set.of("Fruta"));
            ensureFood(foods, "Cereza", "Fresco", "7790000000150", FoodCategory.FRUIT,
                    50, 1, 12.2, 0.3, FoodPreparation.RAW, "USDA FDC 171719", Set.of("Fruta"));
            ensureFood(foods, "Limón", "Fresco", "7790000000151", FoodCategory.FRUIT,
                    29, 1.1, 9.3, 0.3, FoodPreparation.RAW, "USDA FDC 167746", Set.of("Fruta", "Vitamina C"));
            ensureFood(foods, "Frambuesa", "Fresco", "7790000000152", FoodCategory.FRUIT,
                    52, 1.2, 11.9, 0.7, FoodPreparation.RAW, "USDA FDC 173941", Set.of("Fruta"));
            ensureFood(foods, "Mango", "Fresco", "7790000000153", FoodCategory.FRUIT,
                    60, 0.8, 15, 0.4, FoodPreparation.RAW, "USDA FDC 169910", Set.of("Fruta"));
            ensureFood(foods, "Pomelo", "Fresco", "7790000000154", FoodCategory.FRUIT,
                    42, 0.8, 10.7, 0.1, FoodPreparation.RAW, "USDA FDC 174674", Set.of("Fruta", "Vitamina C"));
            ensureFood(foods, "Granada", "Fresco", "7790000000155", FoodCategory.FRUIT,
                    83, 1.7, 18.7, 1.2, FoodPreparation.RAW, "USDA FDC 169934", Set.of("Fruta"));
            ensureFood(foods, "Damasco", "Fresco", "7790000000156", FoodCategory.FRUIT,
                    48, 1.4, 11.1, 0.4, FoodPreparation.RAW, "USDA FDC 171697", Set.of("Fruta"));
            ensureFood(foods, "Higo", "Fresco", "7790000000157", FoodCategory.FRUIT,
                    74, 0.8, 19.2, 0.3, FoodPreparation.RAW, "USDA FDC 173943", Set.of("Fruta"));
            ensureFood(foods, "Moras", "Fresco", "7790000000158", FoodCategory.FRUIT,
                    43, 1.4, 10.2, 0.5, FoodPreparation.RAW, "USDA FDC 173946", Set.of("Fruta"));
            ensureFood(foods, "Papaya", "Fresco", "7790000000159", FoodCategory.FRUIT,
                    43, 0.5, 10.8, 0.3, FoodPreparation.RAW, "USDA FDC 169926", Set.of("Fruta"));
            ensureFood(foods, "Coco rallado", "Fresco", "7790000000160", FoodCategory.FRUIT,
                    354, 3.3, 15.2, 33.5, FoodPreparation.RAW, "USDA FDC 169958", Set.of("Fruta", "Grasas"));
            ensureFood(foods, "Maracuyá", "Fresco", "7790000000161", FoodCategory.FRUIT,
                    97, 2.2, 23.4, 0.7, FoodPreparation.RAW, "USDA FDC 169108", Set.of("Fruta"));
            ensureFood(foods, "Tomate", "Fresco", "7790000000202", FoodCategory.VEGETABLE,
                    20, 1, 4.1, 0, FoodPreparation.RAW, "ARGENFOODS 110", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Lechuga", "Fresco", "7790000000219", FoodCategory.VEGETABLE,
                    15, 1.4, 2.9, 0.2, FoodPreparation.RAW, "USDA FDC 169247", Set.of("Verdura"));
            ensureFood(foods, "Papa", "Fresco", "7790000000226", FoodCategory.VEGETABLE,
                    88, 2.7, 19.3, 0, FoodPreparation.RAW, "ARGENFOODS 87", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Cebolla", "Fresco", "7790000000233", FoodCategory.VEGETABLE,
                    17, 0.8, 3.5, 0, FoodPreparation.RAW, "ARGENFOODS 69", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Zapallo", "Fresco", "7790000000240", FoodCategory.VEGETABLE,
                    27, 0.5, 5.8, 0.2, FoodPreparation.RAW, "ARGENFOODS 430", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Berenjena", "Fresco", "7790000000257", FoodCategory.VEGETABLE,
                    28, 1.1, 5.5, 0.2, FoodPreparation.RAW, "ARGENFOODS 64", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Brócoli", "Fresco", "7790000000264", FoodCategory.VEGETABLE,
                    37, 3.3, 5.5, 0.2, FoodPreparation.RAW, "ARGENFOODS 66", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Espinaca", "Fresco", "7790000000271", FoodCategory.VEGETABLE,
                    24, 3.9, 2, 0, FoodPreparation.RAW, "ARGENFOODS 75", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Repollo", "Fresco", "7790000000272", FoodCategory.VEGETABLE,
                    25, 1.3, 5.8, 0.1, FoodPreparation.RAW, "USDA FDC 169246", Set.of("Verdura"));
            ensureFood(foods, "Zanahoria", "Fresco", "7790000000273", FoodCategory.VEGETABLE,
                    41, 0.9, 9.6, 0.2, FoodPreparation.RAW, "ARGENFOODS 87", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Pepino", "Fresco", "7790000000274", FoodCategory.VEGETABLE,
                    15, 0.7, 3.6, 0.1, FoodPreparation.RAW, "USDA FDC 169248", Set.of("Verdura"));
            ensureFood(foods, "Morrón rojo", "Fresco", "7790000000275", FoodCategory.VEGETABLE,
                    31, 1, 6, 0.3, FoodPreparation.RAW, "ARGENFOODS 88", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Morrón verde", "Fresco", "7790000000276", FoodCategory.VEGETABLE,
                    20, 0.9, 4.6, 0.2, FoodPreparation.RAW, "ARGENFOODS 89", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Calabacín", "Fresco", "7790000000277", FoodCategory.VEGETABLE,
                    17, 1.2, 3.1, 0.3, FoodPreparation.RAW, "USDA FDC 169250", Set.of("Verdura"));
            ensureFood(foods, "Coliflor", "Fresco", "7790000000278", FoodCategory.VEGETABLE,
                    25, 1.9, 5, 0.3, FoodPreparation.RAW, "USDA FDC 169245", Set.of("Verdura", "Keto Friendly"));
            ensureFood(foods, "Chaucha", "Fresco", "7790000000279", FoodCategory.VEGETABLE,
                    31, 1.8, 7, 0.2, FoodPreparation.RAW, "ARGENFOODS 73", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Apio", "Fresco", "7790000000280", FoodCategory.VEGETABLE,
                    16, 0.7, 3, 0.2, FoodPreparation.RAW, "ARGENFOODS 63", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Espárrago", "Fresco", "7790000000281", FoodCategory.VEGETABLE,
                    20, 2.2, 3.9, 0.1, FoodPreparation.RAW, "USDA FDC 168390", Set.of("Verdura"));
            ensureFood(foods, "Remolacha", "Fresco", "7790000000282", FoodCategory.VEGETABLE,
                    43, 1.6, 9.6, 0.2, FoodPreparation.RAW, "ARGENFOODS 68", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Batata", "Fresco", "7790000000283", FoodCategory.VEGETABLE,
                    86, 1.6, 20.1, 0.1, FoodPreparation.RAW, "ARGENFOODS 82", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Choclo", "Fresco", "7790000000284", FoodCategory.VEGETABLE,
                    86, 3.3, 19, 1.2, FoodPreparation.RAW, "ARGENFOODS 85", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Hongos", "Fresco", "7790000000285", FoodCategory.VEGETABLE,
                    22, 3.1, 3.3, 0.3, FoodPreparation.RAW, "USDA FDC 169251", Set.of("Verdura"));
            ensureFood(foods, "Rúcula", "Fresco", "7790000000286", FoodCategory.VEGETABLE,
                    25, 2.6, 3.7, 0.7, FoodPreparation.RAW, "USDA FDC 169253", Set.of("Verdura"));
            ensureFood(foods, "Acelga", "Fresco", "7790000000287", FoodCategory.VEGETABLE,
                    19, 1.8, 3.7, 0.2, FoodPreparation.RAW, "ARGENFOODS 74", Set.of("Verdura", "Argentina"));
            ensureFood(foods, "Radicheta", "Fresco", "7790000000288", FoodCategory.VEGETABLE,
                    23, 1.4, 4.5, 0.3, FoodPreparation.RAW, "USDA FDC 169252", Set.of("Verdura"));
            ensureFood(foods, "Lomo vacuno", "Carnicería", "7790000000301", FoodCategory.PROTEIN,
                    116, 20, 0, 4, FoodPreparation.RAW, "ARGENFOODS 233", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Carne picada magra", "Carnicería", "7790000000707", FoodCategory.PROTEIN,
                    172, 21.4, 0, 9.5, FoodPreparation.RAW, "USDA FoodData Central 90% magra", Set.of("Carne molida", "Picada especial", "Carne vacuna", "Argentina"));
            ensureFood(foods, "Carne picada común", "Carnicería", "7790000000714", FoodCategory.PROTEIN,
                    254, 17.2, 0, 20, FoodPreparation.RAW, "USDA FoodData Central 80% magra", Set.of("Carne molida", "Carne vacuna", "Argentina"));
            ensureFood(foods, "Bife de chorizo", "Carnicería", "7790000000721", FoodCategory.PROTEIN,
                    201, 19, 0, 13.5, FoodPreparation.RAW, "USDA FoodData Central (strip steak)", Set.of("Bife angosto", "Carne vacuna", "Argentina"));
            ensureFood(foods, "Ojo de bife", "Carnicería", "7790000000738", FoodCategory.PROTEIN,
                    263, 19, 0, 21, FoodPreparation.RAW, "USDA FoodData Central (rib eye)", Set.of("Bife ancho", "Carne vacuna", "Argentina"));
            ensureFood(foods, "Cuadril vacuno", "Carnicería", "7790000000745", FoodCategory.PROTEIN,
                    152, 21, 0, 7.2, FoodPreparation.RAW, "USDA FoodData Central (top sirloin)", Set.of("Colita de cuadril", "Carne vacuna", "Argentina"));
            ensureFood(foods, "Entraña vacuna", "Carnicería", "7790000000752", FoodCategory.PROTEIN,
                    205, 20, 0, 13.5, FoodPreparation.RAW, "USDA FoodData Central (skirt steak)", Set.of("Carne vacuna", "Argentina", "Parrilla"));
            ensureFood(foods, "Asado de tira", "Carnicería", "7790000000769", FoodCategory.PROTEIN,
                    291, 17, 0, 25, FoodPreparation.RAW, "USDA FoodData Central (beef short ribs)", Set.of("Costilla", "Carne vacuna", "Argentina", "Parrilla"));
            ensureFood(foods, "Matambre vacuno", "Carnicería", "7790000000776", FoodCategory.PROTEIN,
                    221, 20, 0, 15.5, FoodPreparation.RAW, "Referencia de corte equivalente USDA", Set.of("Carne vacuna", "Argentina", "Parrilla"));
            ensureFood(foods, "Nalga vacuna", "Carnicería", "7790000000318", FoodCategory.PROTEIN,
                    106, 22, 0, 1.7, FoodPreparation.RAW, "ARGENFOODS 510", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Peceto vacuno", "Carnicería", "7790000000325", FoodCategory.PROTEIN,
                    125, 23, 0, 1.9, FoodPreparation.RAW, "ARGENFOODS 514", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Vacío vacuno", "Carnicería", "7790000000332", FoodCategory.PROTEIN,
                    174, 20, 0, 11, FoodPreparation.RAW, "ARGENFOODS 517", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Vacío vacuno", "Carnicería", "7790000000349", FoodCategory.PROTEIN,
                    258, 25.6, 0, 17.3, FoodPreparation.COOKED, "ARGENFOODS 240", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Bife vacuno", "Carnicería", "7790000000356", FoodCategory.PROTEIN,
                    111, 21, 0, 3, FoodPreparation.RAW, "ARGENFOODS 229", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Bife vacuno", "Carnicería", "7790000000363", FoodCategory.PROTEIN,
                    189, 24.6, 0, 10.1, FoodPreparation.COOKED, "ARGENFOODS 227", Set.of("Carne vacuna", "Argentina"));
            ensureFood(foods, "Huevo entero", "Granja", "7790000000400", FoodCategory.PROTEIN,
                    143, 12.6, 0.7, 9.5, FoodPreparation.RAW, "USDA FoodData Central", Set.of("Huevo"));
            ensureFood(foods, "Huevo entero", "Granja", "7790000000417", FoodCategory.PROTEIN,
                    155, 12.6, 1.1, 10.6, FoodPreparation.COOKED, "USDA FoodData Central", Set.of("Huevo"));
            ensureFood(foods, "Merluza", "Pescadería", "7790000000424", FoodCategory.PROTEIN,
                    90, 18.3, 0, 1.3, FoodPreparation.RAW, "LATINFOODS", Set.of("Pescado", "Argentina"));
            ensureFood(foods, "Salmón", "Pescadería", "7790000000431", FoodCategory.PROTEIN,
                    208, 20.4, 0, 13.4, FoodPreparation.COOKED, "USDA FoodData Central", Set.of("Pescado"));
            ensureFood(foods, "Leche entera", "Genérico", "7790000000448", FoodCategory.DAIRY,
                    61, 3.2, 4.8, 3.3, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Lácteo"));
            ensureFood(foods, "Leche descremada", "Genérico", "7790000000455", FoodCategory.DAIRY,
                    34, 3.4, 5, 0.1, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Lácteo"));
            ensureFood(foods, "Queso cremoso", "Genérico", "7790000000462", FoodCategory.DAIRY,
                    300, 18, 2, 24, FoodPreparation.AS_SOLD, "SARA 2 Argentina", Set.of("Lácteo", "Argentina"));
            ensureFood(foods, "Queso crema", "KazaFitness Dairy", "7790000000463", FoodCategory.DAIRY,
                    342, 8, 4, 34, FoodPreparation.AS_SOLD, "ARGENFOODS 102", Set.of("Lácteo", "Alto en Grasas"));
            ensureFood(foods, "Queso mozzarella", "KazaFitness Dairy", "7790000000464", FoodCategory.DAIRY,
                    280, 28, 3, 17, FoodPreparation.AS_SOLD, "ARGENFOODS 105", Set.of("Lácteo", "Alta en Proteína"));
            ensureFood(foods, "Queso cottage", "KazaFitness Dairy", "7790000000465", FoodCategory.DAIRY,
                    98, 11, 3.4, 4.3, FoodPreparation.AS_SOLD, "USDA FDC 171278", Set.of("Lácteo", "Alta en Proteína"));
            ensureFood(foods, "Avena arrollada", "Genérico", "7790000000479", FoodCategory.CEREAL,
                    379, 13.2, 67.7, 6.5, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Cereal"));
            ensureFood(foods, "Pan francés", "Panadería", "7790000000486", FoodCategory.CEREAL,
                    270, 8.5, 57, 1.6, FoodPreparation.AS_SOLD, "SARA 2 Argentina", Set.of("Pan", "Argentina"));
            ensureFood(foods, "Pan integral", "Panadería", "7790000000493", FoodCategory.CEREAL,
                    247, 13, 41, 3.4, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Pan"));
            ensureFood(foods, "Fideos secos", "Genérico", "7790000000509", FoodCategory.CEREAL,
                    371, 13, 75, 1.5, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Pasta"));
            ensureFood(foods, "Fideos cocidos", "Genérico", "7790000000516", FoodCategory.CEREAL,
                    158, 5.8, 30.9, 0.9, FoodPreparation.COOKED, "USDA FoodData Central", Set.of("Pasta"));
            ensureFood(foods, "Fideos integrales cocidos", "Genérico", "7790000000517", FoodCategory.CEREAL,
                    124, 5.3, 26.5, 0.5, FoodPreparation.COOKED, "USDA FDC 169709", Set.of("Pasta", "Argentina"));
            ensureFood(foods, "Quinoa cocida", "Genérico", "7790000000518", FoodCategory.CEREAL,
                    120, 4.4, 21.3, 1.9, FoodPreparation.COOKED, "USDA FDC 168917", Set.of("Cereal", "Sin TACC"));
            ensureFood(foods, "Lentejas cocidas", "Genérico", "7790000000523", FoodCategory.LEGUME,
                    116, 9, 20.1, 0.4, FoodPreparation.COOKED, "USDA FoodData Central", Set.of("Legumbre"));
            ensureFood(foods, "Garbanzos cocidos", "Genérico", "7790000000530", FoodCategory.LEGUME,
                    164, 8.9, 27.4, 2.6, FoodPreparation.COOKED, "USDA FoodData Central", Set.of("Legumbre"));
            ensureFood(foods, "Porotos negros cocidos", "Genérico", "7790000000547", FoodCategory.LEGUME,
                    132, 8.9, 23.7, 0.5, FoodPreparation.COOKED, "USDA FoodData Central", Set.of("Legumbre"));
            ensureFood(foods, "Arvejas cocidas", "Genérico", "7790000000806", FoodCategory.LEGUME,
                    84, 5.4, 15.6, 0.2, FoodPreparation.COOKED, "Promedio de tablas de composición", Set.of("Legumbre", "Argentina"));
            ensureFood(foods, "Porotos alubia cocidos", "Genérico", "7790000000813", FoodCategory.LEGUME,
                    127, 8.7, 22.8, 0.5, FoodPreparation.COOKED, "Promedio de tablas de composición", Set.of("Legumbre", "Porotos", "Argentina"));
            ensureFood(foods, "Porotos colorados cocidos", "Genérico", "7790000000820", FoodCategory.LEGUME,
                    127, 8.7, 22.8, 0.5, FoodPreparation.COOKED, "Promedio de tablas de composición", Set.of("Legumbre", "Porotos", "Argentina"));
            ensureFood(foods, "Soja cocida", "Genérico", "7790000000837", FoodCategory.LEGUME,
                    172, 18.2, 8.4, 9, FoodPreparation.COOKED, "USDA FoodData Central", Set.of("Legumbre", "Alta en Proteina"));
            ensureFood(foods, "Choclo hervido", "Genérico", "7790000000844", FoodCategory.VEGETABLE,
                    96, 3.4, 21, 1.5, FoodPreparation.COOKED, "Promedio de tablas de composición", Set.of("Choclo", "Maiz", "Argentina"));
            ensureFood(foods, "Choclo amarillo en lata", "Genérico", "7790000000851", FoodCategory.VEGETABLE,
                    76, 2.5, 14.7, 1.4, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Choclo", "Maiz", "Enlatado", "Argentina"));
            ensureFood(foods, "Aceite de oliva", "Genérico", "7790000000554", FoodCategory.FAT,
                    884, 0, 0, 100, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Aceite"));
            ensureFood(foods, "Manteca", "Genérico", "7790000000561", FoodCategory.FAT,
                    717, 0.9, 0.1, 81.1, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Lácteo"));
            ensureFood(foods, "Maní tostado", "Genérico", "7790000000578", FoodCategory.FAT,
                    587, 24.4, 21.3, 49.7, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Fruto seco"));
            ensureFood(foods, "Almendras", "Genérico", "7790000000585", FoodCategory.FAT,
                    579, 21.2, 21.6, 49.9, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Fruto seco"));
            ensureFood(foods, "Nueces", "Genérico", "7790000000586", FoodCategory.FAT,
                    654, 15.2, 13.7, 65.2, FoodPreparation.RAW, "ARGENFOODS 128", Set.of("Fruto seco", "Argentina"));
            ensureFood(foods, "Semillas de chía", "Genérico", "7790000000587", FoodCategory.FAT,
                    486, 16.5, 42.1, 30.7, FoodPreparation.RAW, "USDA FDC 170554", Set.of("Semilla"));
            ensureFood(foods, "Pasta de maní", "KazaFitness Select", "7790000000588", FoodCategory.FAT,
                    588, 25, 20, 50, FoodPreparation.AS_SOLD, "USDA FDC 174230", Set.of("Fruto seco"));
            ensureFood(foods, "Jugo de naranja", "Genérico", "7790000000592", FoodCategory.FRUIT,
                    45, 0.7, 10.4, 0.2, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Bebida"));
            ensureFood(foods, "Papas fritas de paquete", "Genérico", "7790000000608", FoodCategory.OTHER,
                    536, 7, 53, 35, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Snack"));
            ensureFood(foods, "Chocolate con leche", "Genérico", "7790000000615", FoodCategory.OTHER,
                    535, 7.7, 59.4, 29.7, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Snack"));
            ensureFood(foods, "Alfajor de chocolate simple", "Genérico", "7790000000868", FoodCategory.SWEET,
                    430, 5.5, 62, 18, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Alfajor", "Golosina", "Kiosco", "Argentina"));
            ensureFood(foods, "Alfajor de chocolate triple", "Genérico", "7790000000875", FoodCategory.SWEET,
                    420, 5.5, 61, 17, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Alfajor", "Triple", "Golosina", "Kiosco", "Argentina"));
            ensureFood(foods, "Alfajor de maicena", "Genérico", "7790000000882", FoodCategory.SWEET,
                    410, 5, 65, 14.5, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Alfajor", "Maicena", "Dulce de leche", "Argentina"));
            ensureFood(foods, "Dulce de leche", "Genérico", "7790000000899", FoodCategory.SWEET,
                    315, 6, 57, 7, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Dulce", "Argentina"));
            ensureFood(foods, "Galletitas de agua", "Genérico", "7790000000905", FoodCategory.BAKERY,
                    430, 10, 72, 12, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Galletitas", "Crackers", "Argentina"));
            ensureFood(foods, "Galletitas saladas tipo crackers", "Genérico", "7790000000912", FoodCategory.BAKERY,
                    455, 9, 68, 16, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Galletitas", "Saladas", "Crackers", "Argentina"));
            ensureFood(foods, "Galletitas dulces de vainilla", "Genérico", "7790000000929", FoodCategory.SWEET,
                    440, 6, 73, 14, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Galletitas", "Dulces", "Vainilla", "Argentina"));
            ensureFood(foods, "Galletitas de chocolate", "Genérico", "7790000000936", FoodCategory.SWEET,
                    470, 6, 68, 20, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Galletitas", "Chocolate", "Argentina"));
            ensureFood(foods, "Galletitas pepas con membrillo", "Genérico", "7790000000943", FoodCategory.SWEET,
                    435, 5, 71, 15, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Galletitas", "Pepas", "Membrillo", "Argentina"));
            ensureFood(foods, "Oblea rellena", "Genérico", "7790000000950", FoodCategory.SWEET,
                    505, 6, 64, 25, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Oblea", "Golosina", "Kiosco", "Argentina"));
            ensureFood(foods, "Gomitas frutales", "Genérico", "7790000000967", FoodCategory.SWEET,
                    340, 5, 79, 0.2, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Gomitas", "Golosina", "Kiosco"));
            ensureFood(foods, "Caramelos duros", "Genérico", "7790000000974", FoodCategory.SWEET,
                    390, 0, 97, 0, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Caramelo", "Golosina", "Kiosco"));
            ensureFood(foods, "Turrón de maní", "Genérico", "7790000000981", FoodCategory.SWEET,
                    500, 12, 55, 26, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Turron", "Mani", "Golosina", "Argentina"));
            ensureFood(foods, "Bocadito de pasta de maní", "Genérico", "7790000000998", FoodCategory.SWEET,
                    535, 13, 50, 32, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Mantecol", "Mani", "Golosina", "Argentina"));
            ensureFood(foods, "Barrita de cereal", "Genérico", "7790000001001", FoodCategory.CEREAL,
                    390, 6, 70, 10, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Barrita", "Cereal", "Snack"));
            ensureFood(foods, "Chizitos de maíz", "Genérico", "7790000001018", FoodCategory.SNACK,
                    530, 6, 56, 31, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Chizitos", "Snack", "Maiz", "Kiosco", "Argentina"));
            ensureFood(foods, "Palitos salados", "Genérico", "7790000001025", FoodCategory.SNACK,
                    470, 10, 72, 16, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Palitos", "Snack", "Salado", "Argentina"));
            ensureFood(foods, "Nachos de maíz", "Genérico", "7790000001032", FoodCategory.SNACK,
                    500, 7, 58, 27, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Nachos", "Snack", "Maiz"));
            ensureFood(foods, "Pochoclo salado", "Genérico", "7790000001049", FoodCategory.CEREAL,
                    430, 9, 57, 19, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Pochoclo", "Popcorn", "Snack", "Argentina"));
            ensureFood(foods, "Pochoclo acaramelado", "Genérico", "7790000001056", FoodCategory.OTHER,
                    410, 4, 78, 9, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Pochoclo", "Popcorn", "Dulce", "Argentina"));
            ensureFood(foods, "Azúcar", "Genérico", "7790000000622", FoodCategory.OTHER,
                    387, 0, 100, 0, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Condimento"));
            ensureFood(foods, "Mayonesa", "Genérico", "7790000000639", FoodCategory.FAT,
                    680, 1, 1, 75, FoodPreparation.AS_SOLD, "Promedio de etiquetas argentinas", Set.of("Condimento", "Argentina"));
            ensureFood(foods, "Pizza de mozzarella", "Genérico", "7790000000646", FoodCategory.OTHER,
                    266, 11.4, 33.3, 9.8, FoodPreparation.COOKED, "USDA FoodData Central", Set.of("Comida preparada"));
            ensureFood(foods, "Empanada de carne", "Genérico", "7790000000653", FoodCategory.OTHER,
                    260, 10, 28, 12, FoodPreparation.COOKED, "SARA 2 Argentina", Set.of("Comida preparada", "Argentina"));
            ensureFood(foods, "Mate cocido sin azúcar", "Genérico", "7790000000660", FoodCategory.OTHER,
                    1, 0, 0.2, 0, FoodPreparation.COOKED, "USDA FoodData Central", Set.of("Bebida", "Argentina"));
            ensureFood(foods, "Café negro sin azúcar", "Genérico", "7790000000677", FoodCategory.OTHER,
                    2, 0.1, 0, 0, FoodPreparation.COOKED, "USDA FoodData Central", Set.of("Bebida"));
            ensureFood(foods, "Ketchup", "Genérico", "7790000000684", FoodCategory.OTHER,
                    112, 1.3, 26, 0.2, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Condimento"));
            ensureFood(foods, "Sal de mesa", "Genérico", "7790000000691", FoodCategory.OTHER,
                    0, 0, 0, 0, FoodPreparation.AS_SOLD, "USDA FoodData Central", Set.of("Condimento"));
            setServing(foods, "7790000000028", "Taza cocida", 158);
            setServing(foods, "7790000000035", "Palta mediana", 201);
            setServing(foods, "7790000000066", "Banana mediana", 118);
            setServing(foods, "7790000000103", "Manzana mediana", 182);
            setServing(foods, "7790000000110", "Pera mediana", 178);
            setServing(foods, "7790000000127", "Durazno mediano", 150);
            setServing(foods, "7790000000134", "Frutilla mediana", 12);
            setServing(foods, "7790000000142", "Naranja mediana", 131);
            setServing(foods, "7790000000143", "Mandarina mediana", 100);
            setServing(foods, "7790000000145", "Kiwi mediano", 69);
            setServing(foods, "7790000000153", "Mango mediano", 200);
            setServing(foods, "7790000000202", "Tomate mediano", 123);
            setServing(foods, "7790000000226", "Papa mediana", 173);
            setServing(foods, "7790000000233", "Cebolla mediana", 110);
            setServing(foods, "7790000000272", "Repollo chico", 500);
            setServing(foods, "7790000000273", "Zanahoria mediana", 61);
            setServing(foods, "7790000000274", "Pepino mediano", 200);
            setServing(foods, "7790000000282", "Remolacha mediana", 136);
            setServing(foods, "7790000000284", "Choclo", 150);
            setPreparationGroup(foods, "CHICKEN_BREAST", "7790000000011", "7790000000073");
            setPreparationGroup(foods, "BEEF_VACIO", "7790000000332", "7790000000349");
            setPreparationGroup(foods, "BEEF_STEAK", "7790000000356", "7790000000363");
            setPreparationGroup(foods, "WHOLE_EGG", "7790000000400", "7790000000417");
            setPreparationGroup(foods, "PASTA", "7790000000509", "7790000000516");
            setServing(foods, "7790000000400", "Huevo mediano", 50);
            setServing(foods, "7790000000417", "Huevo mediano", 50);
            setServing(foods, "7790000000448", "Taza", 240);
            setServing(foods, "7790000000455", "Taza", 240);
            setServing(foods, "7790000000462", "Porción", 30);
            setServing(foods, "7790000000486", "Unidad", 50);
            setServing(foods, "7790000000493", "Rodaja", 30);
            setServing(foods, "7790000000646", "Porción", 120);
            setServing(foods, "7790000000653", "Unidad", 90);
            setServing(foods, "7790000000660", "Taza", 240);
            setServing(foods, "7790000000677", "Taza", 240);
            setServing(foods, "7790000000806", "Media taza", 80);
            setServing(foods, "7790000000813", "Media taza", 90);
            setServing(foods, "7790000000820", "Media taza", 90);
            setServing(foods, "7790000000837", "Media taza", 85);
            setServing(foods, "7790000000844", "Choclo mediano", 150);
            setServing(foods, "7790000000851", "Media taza", 85);
            setServing(foods, "7790000000868", "Unidad", 55);
            setServing(foods, "7790000000875", "Unidad", 70);
            setServing(foods, "7790000000882", "Unidad", 60);
            setServing(foods, "7790000000899", "Cucharada", 20);
            setServing(foods, "7790000000905", "4 galletitas", 30);
            setServing(foods, "7790000000912", "6 galletitas", 30);
            setServing(foods, "7790000000929", "5 galletitas", 35);
            setServing(foods, "7790000000936", "4 galletitas", 36);
            setServing(foods, "7790000000943", "3 galletitas", 36);
            setServing(foods, "7790000000950", "Unidad", 22);
            setServing(foods, "7790000000967", "Porción", 30);
            setServing(foods, "7790000000974", "Unidad", 6);
            setServing(foods, "7790000000981", "Unidad", 25);
            setServing(foods, "7790000000998", "Porción", 25);
            setServing(foods, "7790000001001", "Unidad", 23);
            setServing(foods, "7790000001018", "Paquete chico", 35);
            setServing(foods, "7790000001025", "Porción", 30);
            setServing(foods, "7790000001032", "Porción", 30);
            setServing(foods, "7790000001049", "Taza", 12);
            setServing(foods, "7790000001056", "Taza", 20);
            }

            if (!demoUsersEnabled) return;
            ensureAdmin(users, passwordEncoder);

            if (!users.existsByEmailIgnoreCase("alex@kazadesarrollos.com")) {
                AppUser user = new AppUser();
                user.setFullName("Alex Rivera");
                user.setEmail("alex@kazadesarrollos.com");
                user.setPasswordHash(passwordEncoder.encode("password123"));
                user.setWeightKg(BigDecimal.valueOf(75));
                user.setHeightCm(BigDecimal.valueOf(180));
                user.setTargetWeightKg(BigDecimal.valueOf(72));
                user.setBirthDate(LocalDate.now().minusYears(28));
                user.setGender(Gender.MALE);
                user.setActivityLevel(ActivityLevel.VERY_ACTIVE);
                user.setGoal(FitnessGoal.LOSE);
                user.setNutritionStyle("Keto");
                users.save(user);
                if (nutritionPlans.findByUserOrderByStartDateDesc(user).isEmpty()) {
                    NutritionPlan plan = new NutritionPlan();
                    plan.setUser(user);
                    plan.setName("Balanceado");
                    plan.setDailyCalories(user.getDailyCalorieGoal());
                    plan.setProteinPercent(BigDecimal.valueOf(30));
                    plan.setCarbsPercent(BigDecimal.valueOf(40));
                    plan.setFatPercent(BigDecimal.valueOf(30));
                    plan.setProteinGoalGrams(user.getProteinGoalGrams());
                    plan.setCarbsGoalGrams(user.getCarbsGoalGrams());
                    plan.setFatGoalGrams(user.getFatGoalGrams());
                    plan.setStartDate(LocalDate.now().minusYears(1));
                    nutritionPlans.save(plan);
                }

                Food chicken = foods.findByBarcode("7790000000011").orElseThrow();
                Food rice = foods.findByBarcode("7790000000028").orElseThrow();
                Food yogurt = foods.findByBarcode("7790000000042").orElseThrow();
                addLog(foodLogs, user, chicken, MealType.LUNCH, 150);
                addLog(foodLogs, user, rice, MealType.LUNCH, 100);
                addLog(foodLogs, user, yogurt, MealType.AFTERNOON_SNACK, 200);
                WaterLog water = new WaterLog();
                water.setUser(user);
                water.setLogDate(LocalDate.now());
                water.setLiters(BigDecimal.valueOf(1.5));
                waterLogs.save(water);
            }
        };
    }

    private static void ensureAdmin(UserRepository users, PasswordEncoder passwordEncoder) {
        if (users.existsByEmailIgnoreCase("admin@gmail.com")) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setFullName("Admin");
        admin.setEmail("admin@gmail.com");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setRole(Role.ADMIN);
        admin.setNutritionStyle("Balanceado");
        users.save(admin);
    }

    private static void ensureFood(FoodRepository foods, String name, String brand, String barcode, FoodCategory category,
            int calories, double protein, double carbs, double fat, FoodPreparation preparation, String preparationSource, Set<String> tags) {
        category = seedCategory(name, category, tags);
        var existing = foods.findByBarcode(barcode);
        if (existing.isPresent()) {
            Food food = existing.get();
            food.setCategory(category);
            food.setPreparation(preparation);
            food.setPreparationSource(preparationSource);
            foods.save(food);
            return;
        }
        foods.save(food(name, brand, barcode, category, calories, protein, carbs, fat, preparation, preparationSource, tags));
    }

    private static FoodCategory seedCategory(String name, FoodCategory category, Set<String> tags) {
        String value = (name + " " + String.join(" ", tags)).toLowerCase();
        if (category == FoodCategory.PROTEIN && (value.contains("carne") || value.contains("vacuno") || value.contains("pollo"))) return FoodCategory.MEAT;
        if (category == FoodCategory.CEREAL && (value.contains("pan") || value.contains("galletita") || value.contains("cracker"))) return FoodCategory.BAKERY;
        if (category == FoodCategory.OTHER && value.contains("chocolate")) return FoodCategory.SWEET;
        if (category == FoodCategory.OTHER && (value.contains("snack") || value.contains("papas fritas"))) return FoodCategory.SNACK;
        return category;
    }

    private static Food food(String name, String brand, String barcode, FoodCategory category, int calories,
            double protein, double carbs, double fat, FoodPreparation preparation, String preparationSource, Set<String> tags) {
        Food food = new Food();
        food.setName(name);
        food.setBrand(brand);
        food.setBarcode(barcode);
        food.setCategory(category);
        food.setBaseUnit(FoodUnit.GRAM);
        food.setBaseQuantity(BigDecimal.valueOf(100));
        food.setCalories(calories);
        food.setProteinGrams(BigDecimal.valueOf(protein));
        food.setCarbsGrams(BigDecimal.valueOf(carbs));
        food.setFatGrams(BigDecimal.valueOf(fat));
        food.setPreparation(preparation);
        food.setPreparationSource(preparationSource);
        food.setTags(tags);
        return food;
    }

    private static void setServing(FoodRepository foods, String barcode, String name, double grams) {
        foods.findByBarcode(barcode).ifPresent(food -> {
            food.setServingName(name);
            food.setServingWeightGrams(BigDecimal.valueOf(grams));
            foods.save(food);
        });
    }

    private static void setPreparationGroup(FoodRepository foods, String group, String... barcodes) {
        for (String barcode : barcodes) {
            foods.findByBarcode(barcode).ifPresent(food -> {
                food.setPreparationGroup(group);
                foods.save(food);
            });
        }
    }

    private static void addLog(FoodLogRepository foodLogs, AppUser user, Food food, MealType mealType, int quantity) {
        BigDecimal ratio = BigDecimal.valueOf(quantity).divide(BigDecimal.valueOf(100));
        FoodLog log = new FoodLog();
        log.setUser(user);
        log.setFood(food);
        log.setMealType(mealType);
        log.setLogDate(LocalDate.now());
        log.setQuantity(BigDecimal.valueOf(quantity));
        log.setUnit(FoodUnit.GRAM);
        log.setCalories(BigDecimal.valueOf(food.getCalories()).multiply(ratio).intValue());
        log.setProteinGrams(food.getProteinGrams().multiply(ratio));
        log.setCarbsGrams(food.getCarbsGrams().multiply(ratio));
        log.setFatGrams(food.getFatGrams().multiply(ratio));
        foodLogs.save(log);
    }
}

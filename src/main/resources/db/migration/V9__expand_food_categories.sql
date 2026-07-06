ALTER TABLE food DROP CONSTRAINT IF EXISTS food_category_check;

ALTER TABLE food
    ADD CONSTRAINT food_category_check
    CHECK (category IN (
        'PROTEIN', 'MEAT', 'DAIRY', 'FRUIT', 'VEGETABLE', 'LEGUME', 'CEREAL',
        'BAKERY', 'BEVERAGE', 'SWEET', 'SNACK', 'FAT', 'OTHER'
    ));

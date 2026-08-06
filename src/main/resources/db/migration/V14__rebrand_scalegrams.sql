UPDATE food
SET brand = REPLACE(brand, 'KazaFitness', 'ScaleGrams')
WHERE brand LIKE 'KazaFitness%';

UPDATE users
SET email = 'alex@scalegrams.local'
WHERE LOWER(email) = 'alex@kazadesarrollos.com'
  AND NOT EXISTS (
      SELECT 1
      FROM users
      WHERE LOWER(email) = 'alex@scalegrams.local'
  );

UPDATE food
SET image_url = NULL,
    image_object_key = NULL
WHERE image_url IS NOT NULL OR image_object_key IS NOT NULL;

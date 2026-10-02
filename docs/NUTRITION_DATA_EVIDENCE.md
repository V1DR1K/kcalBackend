# SG005: composición pendiente de verificar

La auditoría desplegada documentó una variante cocida de Pechuga de Pollo con todos los macros en cero y una descripción derivada con factor 0,7500. Ese origen no está presente en los procesos de derivación del código local. El sembrador contiene variantes diferentes con códigos de barras propios; no es evidencia suficiente para sustituir la entidad observada.

La respuesta pública advierte sobre composición pendiente de verificar cuando un alimento de categoría PROTEIN contiene energía y los tres macros en cero. Los valores no cambian. Los ceros válidos se conservan; un cero individual no dispara esta regla. Los valores nulos se mantienen nulos en conversión, registro y respuestas. El historial utiliza la energía guardada en cada consumo, sin recalcular registros anteriores.

Los subtotales conocidos de nutrientes se exponen como knownValue y complete; value se mantiene desconocido si la suma incluye contribuciones faltantes. V45 agrega estos campos al snapshot sin alterar valores existentes. La interfaz muestra advertencia antes de registrar y permite elegir otra variante.

## Identificación de la entidad de producción (lectura autenticada, 2026-10-01)

La consulta de /api/foods?q=pollo&size=50 identifica la variante problemática como id 306, Pechuga de Pollo, MEAT/COOKED: calories=0 y proteinGrams/carbsGrams/fatGrams=null. La UI anterior convertía esos nulos en ceros. cookedYieldSource=OPENAI y cookedYieldFactor=1; no contiene composición validada. Las variantes verificables de la misma búsqueda tienen IDs distintos; no corresponde trasladar sus datos automáticamente.

El id 2 es crudo (113 kcal, 22,5 g de proteínas, 0 g de carbohidratos, 2,6 g de grasas), con rendimiento estimado GEMINI=0,75. El id 1 es cocido y conserva 31 g de proteínas y 3,6 g de grasas. Se ofrecen como alternativas con preparación/base/rendimiento explícitos, sin sustituir id 306 ni modificar registros anteriores. La advertencia de incompletitud cubre los nulos independientemente de categoría; la de composición totalmente cero también cubre MEAT. Estas consultas no crearon alimentos ni consumos y cerraron la sesión de consulta.

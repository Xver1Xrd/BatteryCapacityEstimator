# Room и Hilt поставляют собственные consumer-rules.
# Держим только то, что нужно рефлексии в этом проекте.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# dumpsys/sysfs-парсеры не используют рефлексию — правил не требуется.
# Данные Room-сущностей сериализуются компилятором Room, обфускация безопасна.

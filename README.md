# Battery Capacity Estimator

Измеряет **фактическую ёмкость аккумулятора в мА·ч** и сравнивает её с паспортной
(design) ёмкостью — показывает реальный износ батареи, а не проценты из статус-бара.

Android 8.0+ (API 26). Kotlin, Jetpack Compose (Material 3), Hilt, Coroutines/Flow,
Room, DataStore, WorkManager, Foreground Service.

## Почему «100%» в шторке — не то же самое, что здоровая батарея

ОС показывает **state of charge (SOC)** — долю от *текущей* полной ёмкости.
Изношенная на 30 % батарея при 100 % SOC хранит всего 70 % паспортных мА·ч.
Приложение измеряет знаменатель этой дроби.

## Методы оценки (приоритет)

| | Метод | Источник | Точность |
|---|---|---|---|
| **C** | Прямой отчёт контроллера: `charge_full` / `charge_full_design` (или energy-пары через номинальное напряжение) | sysfs `/sys/class/power_supply/*`, dumpsys | ±2–5 %, но зависит от честности OEM |
| **A** | Кулон-счёт: `C = ΔQ / ΔSOC`, где ΔQ — изменение `BATTERY_PROPERTY_CHARGE_COUNTER` между двумя точками SOC | BatteryManager API | ±3–7 % за полный цикл 15→99 % |
| **B** | Интегрирование тока трапециями `ΔQ = ∫I·dt`, когда счётчик заряда скрыт OEM | `CURRENT_NOW` | ±5–10 % |

Методы A и B считаются раздельно для фаз разряда и заряда: во время заряда часть
тока уходит не в банку (нагрев, CV-фаза), смешивание фаз даёт систематику до −20 %.
Итоговый результат — взвешенная по качеству фаз оценка с доверительным интервалом.

## Как правильно замерять (инструкция для пользователя)

1. Разрядите телефон до **ниже 15 %**, в идеале до автоотключения.
2. Подключите **штатную зарядку** (не беспроводную — ток шумнее) и не трогайте
   телефон. Приложение само соберёт данные через Foreground Service.
3. Дождитесь **≥ 99 %**. Чем шире диапазон SOC, тем меньше погрешность:
   полный проход 0→100 % даёт ~±3 %, короткий 20→90 % — уже ~±10 %.
4. Результат появится на экране «Калибровка» и сохранится в историю.

Замер корректен только на стабильной температуре 0–45 °C; паузы (сон, отключение
зарядки, перезагрузка) автоматически переводят сессию в PAUSED и возобновляются.

## Ограничения точности

- SOC от ОС квантован (шаг 1–5 %) → границы диапазона известны с ошибкой ±0.5 %;
- верхняя зона SOC ≥ 95 % нелинейна (CV-фаза) и исключается из расчёта;
- первые ~3 минуты быстрой зарядки отбрасываются (CC-CV ramp-up);
- выбросы датчика тока > 5 А глушатся медианным фильтром + линейной
  интерполяцией пробелов, чтобы время не выпадало из интеграла;
- некоторые OEM (Xiaomi/MIUI старых версий) скрывают charge_counter и/или
  sysfs-узлы — тогда остаётся метод B с пониженной точностью;
- dumpsys battery требует permission DUMP, недоступный обычным приложениям, —
  источник используется как опциональный fallback.

Доверительный интервал ±X мА·ч считается по дисперсиям: квантование SOC,
разброс тока после фильтрации, длина диапазона. Он показывается в UI честно.

## Структура проекта

```
app/src/main/kotlin/dev/xverlxrd/batterycapacity/
├── domain/          # чистая логика без Android: модели, policy/, filter/,
│   ├── estimator/   # CoulombCounter, CurrentIntegrator, CapacityEstimator,
│   │                # ConfidenceCalculator, SocPlateauDetector, DeviceCapacityTable
│   ├── repository/  # интерфейсы
│   └── usecase/
├── data/            # datasource/{sysfs,dumpsys,system}, local (Room),
│                    # datastore (DataStore), repository/ (имплементации)
├── di/              # Hilt-модули
├── service/         # MeasurementForegroundService, CheckpointWorker, BootReceiver
└── ui/              # Compose: dashboard, live, calibration (wizard), history, settings
```

## Сборка

```bash
./gradlew :app:assembleDebug        # APK: app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # unit-тесты (27 шт., включая Robolectric)
./gradlew :app:connectedDebugAndroidTest  # compose UI-тесты (нужно устройство)
```

Требуется JDK 17 (`org.gradle.java.home` уже прописан в `gradle.properties`)
и Android SDK с platform android-36.

## Порт на iOS?

Кратко: честного аналога нет — iOS не отдаёт ни charge counter, ни ток, ни
sysfs-эквиваленты сторонним приложениям. Подробности и стратегия ручного ввода:
[docs/PORTING_IOS.md](docs/PORTING_IOS.md).

## Проверенные устройства

Логика источников покрыта unit-тестами на фикстурах реальных деревьев sysfs:
Google Pixel (узлы `battery` + `maxfg`, V/I в одном узле, charge_full в другом),
складные с двумя банками (агрегация + взвешенный SOC), устройства с
energy-based fuel gauge (µWh вместо µAh).

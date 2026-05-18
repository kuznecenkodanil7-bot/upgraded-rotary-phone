# Staff Join Notifier

Клиентский Fabric-мод для Minecraft Java 1.21.11.

Мод отслеживает обновления списка игроков сервера и отправляет сообщение в чат, если на сервер зашёл игрок, у которого перед ником есть одна из staff-приписок:

- `moder`
- `st.moder`
- `helper`
- `st.helper`
- `admin`
- `owner`

Сообщение по умолчанию:

```text
Игрок {rank} {player} зашёл на сервер
```

Пример:

```text
Игрок admin Steve зашёл на сервер
```

## Как это работает

Мод не читает серверный чат. Он ловит клиентский пакет обновления tab-list `PlayerListS2CPacket` и проверяет `displayName` / scoreboard team prefix игрока. Поэтому он работает даже на серверах, где обычное сообщение входа отключено или изменено.

После твоего подключения к серверу мод первые 5 секунд игнорирует список игроков, чтобы не спамить сообщениями обо всех, кто уже был онлайн.

## Установка для игрока

1. Установи Fabric Loader для Minecraft `1.21.11`.
2. Установи Fabric API.
3. Собери мод или скачай `.jar` из GitHub Actions.
4. Положи `.jar` в папку:

```text
.minecraft/mods
```

## Сборка локально

Нужны Java 21 и Gradle 8.14+.

```bash
gradle build
```

Готовый `.jar` появится здесь:

```text
build/libs/staff-join-notifier-1.0.0.jar
```

## Загрузка на GitHub

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/USERNAME/staff-join-notifier.git
git push -u origin main
```

После push GitHub Actions сам соберёт `.jar` и прикрепит его как artifact.

## Настройка

После первого запуска появится файл:

```text
.minecraft/config/staff-join-notifier.json
```

Пример конфига:

```json
{
  "enabled": true,
  "prefixes": [
    "st.moder",
    "st.helper",
    "moder",
    "helper",
    "admin",
    "owner"
  ],
  "messageTemplate": "Игрок {rank} {player} зашёл на сервер",
  "joinGraceTicks": 100,
  "checkTicks": 200
}
```

### Переменные для сообщения

- `{player}` — ник игрока.
- `{rank}` — найденная приписка.
- `{prefix}` — то же самое, что `{rank}`.

Можно сделать, например:

```json
"messageTemplate": "На сервер зашёл сотрудник: {rank} {player}"
```

Если сообщение начинается с `/`, мод отправит его как команду. Например:

```json
"messageTemplate": "/msg Friend На сервер зашёл {rank} {player}"
```

## Важно

Автоматическая отправка сообщений может быть запрещена правилами некоторых серверов. Используй мод только там, где это разрешено.

## Структура проекта

```text
staff-join-notifier/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── README.md
├── LICENSE
├── .github/workflows/build.yml
└── src/main/
    ├── java/ru/vgoqcnss4002/staffjoin/
    │   ├── StaffJoinNotifierClient.java
    │   ├── StaffJoinConfig.java
    │   └── mixin/ClientPlayNetworkHandlerMixin.java
    └── resources/
        ├── fabric.mod.json
        └── staff_join_notifier.mixins.json
```

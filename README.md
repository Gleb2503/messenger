#  Messenger — Android Application

Современный мессенджер для обмена текстовыми сообщениями с простым и интуитивным интерфейсом.

---
##  О проекте

Messenger — это Android-приложение для обмена сообщениями, разработанное с использованием современных практик и архитектурных паттернов. Приложение предоставляет возможность регистрации, авторизации, просмотра списка чатов, отправки и получения сообщений в реальном времени.

**Основные возможности:**
-  Регистрация и авторизация пользователей (JWT)
-  Список чатов с закреплёнными диалогами
-  Личные и групповые чаты
-  Отправка текстовых сообщений
-  Статусы онлайн/офлайн
-  Индикаторы непрочитанных сообщений
-  Тёмная и светлая тема
-  Офлайн-режим с кэшированием

---

##  Дизайн

Полные макеты и скриншоты всех экранов доступны в папке:

 **[docs/schemas/](./docs/schemas/)**

### Экраны приложения:
| Экран | Скриншоты состояний |
|-------|---------------------|
| **Login** | [Default](./docs/schemas/1_login/Login_default.png) • [Loading](./docs/schemas/1_login/Login_loading.png) • [Error](./docs/schemas/1_login/Login_error.png) |
| **Register** | [Default](./docs/schemas/2_register/Register_default.png) • [Loading](./docs/schemas/2_register/Register_loading.png) • [Error](./docs/schemas/2_register/Register_error.png) |
| **Chat List** | [Default](./docs/schemas/3_chatList/Chat_list_default.png) • [Loading](./docs/schemas/3_chatList/Chat_list_loading.png) • [Empty](./docs/schemas/3_chatList/Chat_list_empty.png) • [Error](./docs/schemas/3_chatList/Chat_list_error.png) |
| **Individual Chat** | [Default](./docs/schemas/4_individualChat/Individual_chat_default.png) • [Empty](./docs/schemas/4_individualChat/Individual_chat_empty.png) • [Error](./docs/schemas/4_individualChat/Individual_chat_error.png) |
| **Group Chat** | [Default](./docs/schemas/5_groupChat/Group_chat_default.png) • [Empty](./docs/schemas/5_groupChat/Group_chat_empty.png) • [Error](./docs/schemas/5_groupChat/Group_chat_error.png) |
| **Profile** | [Default](./docs/schemas/6_profile/Profile_default.png) • [Loading](./docs/schemas/6_profile/Profile_loading.png) |
| **Settings** | [Default](./docs/schemas/7_settings/Settings_default.png) |
| **Chat Info** | [Default](./docs/schemas/8_chatInfo/Chat_info.png) |
| **Contacts** | [Default](./docs/schemas/9_contacts/Contacts_default.png) • [Empty](./docs/schemas/9_contacts/Contacts_empty.png) |
| **Search** | [Default](./docs/schemas/10_search/Search_default.png) • [Empty Results](./docs/schemas/10_search/Search_empty_results.png) |
| **Notifications** | [Default](./docs/schemas/11_notifications/Notifications.png) |
| **Media Viewer** | [Default](./docs/schemas/12_mediaViewer/Media_viewer_default.png) • [Loading](./docs/schemas/12_mediaViewer/Media_viewer_loading.png) |

### Цветовая палитра:

| Цвет | HEX | Использование |
|------|-----|---------------|
| Primary | `#44ACCA` | Кнопки, акценты, исходящие сообщения |
| Primary Variant | `#3B8297` | Кнопка при нажатии |
| Background | `#F9FAFB` | Фон экрана (светлая тема) |
| Surface | `#FFFFFF` | Карточки, поля ввода |
| Text Primary | `#111827` | Основной текст |
| Text Secondary | `#6B7280` | Вторичный текст |
| Error | `#EF4444` | Ошибки, удаление |
| Success/Online | `#10B981` | Статус онлайн, успех |

---

##  Технологии

### Frontend (Android)

| Технология | Версия               | Назначение |
|------------|----------------------|------------|
| **Язык** | Java 21              | Основной язык разработки |
| **UI** | XML Layouts          | Верстка экранов |
| **Design** | Material Design 3    | Компоненты и стили |
| **Архитектура** | MVVM + Repository    | Разделение слоев |
| **Network** | Retrofit 2 + OkHttp  | HTTP-запросы к API |
| **JSON** | Gson                 | Сериализация/десериализация |
| **Image Loading** | Glide 4              | Загрузка изображений |
| **Local DB** | Room 2               | Локальное кэширование |
| **Storage** | SharedPreferences    | Хранение настроек и токенов |
| **Navigation** | Navigation Component | Навигация между экранами |

### Backend (Spring Boot)

| Технология | Назначение |
|------------|------------|
| Spring Boot 3 | REST API |
| Spring Security | JWT авторизация |
| Spring Data JPA | Работа с БД |
| PostgreSQL | База данных |
| WebSocket | Real-time сообщения |


---



Приложение построено по паттерну **MVVM (Model-View-ViewModel)** с использованием **Repository Pattern**.


**Преимущества:**
- Четкое разделение ответственности между слоями
- ViewModel сохраняет состояние при повороте экрана
- Легко тестировать бизнес-логику без UI
- Простое добавление новых функций

    ##  Навигация между экранами
    ## 🗺️ Навигация между экранами

    ```mermaid
    flowchart TD
        %% Стилизация узлов
        classDef auth fill:#FFE5E5,stroke:#EF4444,stroke-width:2px
        classDef main fill:#E5F3FF,stroke:#3B82F6,stroke-width:2px
        classDef chat fill:#E5FFE5,stroke:#10B981,stroke-width:2px
        classDef settings fill:#FFF5E5,stroke:#F59E0B,stroke-width:2px
    
        %% Экраны авторизации
        Login[ Login]:::auth
        Register[ Register]:::auth
    
        %% Главный экран
        ChatList[ Chat List]:::main
    
        %% Чаты
        IndividualChat[Individual Chat]:::chat
        GroupChat[Group Chat]:::chat
        ChatInfo[ℹChat Info]:::settings
    
        %% Профиль и настройки
        Profile[ Profile]:::settings
        Settings[ Settings]:::settings
        Notifications[ Notifications]:::settings
    
        %% Поиск и контакты
        Search[ Search]:::main
        Contacts[Contacts]:::main
    
        %% Медиа
        MediaViewer[Media Viewer]:::chat
    
        %% Навигация авторизации
        Login -->|Нет аккаунта| Register
        Register -->|Есть аккаунт| Login
        Login -->|Успешный вход| ChatList
        Register -->|Успешная регистрация| ChatList
    
        %% Навигация из Chat List
        ChatList -->|Тап на чат| IndividualChat
        ChatList -->|Тап на группу| GroupChat
        ChatList -->|Тап на профиль| Profile
        ChatList -->|Тап на поиск| Search
        ChatList -->|FAB Новый чат| Contacts
        ChatList -->|Настройки| Settings
    
        %% Навигация из чатов
        IndividualChat -->|Шапка| ChatInfo
        IndividualChat -->|Назад| ChatList
        IndividualChat -->|Медиа| MediaViewer
    
        GroupChat -->|Шапка| ChatInfo
        GroupChat -->|Назад| ChatList
        GroupChat -->|Медиа| MediaViewer
    
        ChatInfo -->|Назад| IndividualChat
        ChatInfo -->|Назад| GroupChat
    
        %% Профиль и настройки
        Profile -->|Настройки| Settings
        Profile -->|Назад| ChatList
        Profile -->|Редактировать| Profile
    
        Settings -->|Назад| Profile
        Settings -->|Назад| ChatList
        Settings -->|Уведомления| Notifications
        Settings -->|Выйти| Login
    
        Notifications -->|Назад| Settings
    
        %% Поиск и контакты
        Search -->|Результат| IndividualChat
        Search -->|Назад| ChatList
    
        Contacts -->|Выбор контакта| IndividualChat
        Contacts -->|Назад| ChatList
    
        %% Медиа
        MediaViewer -->|Назад| IndividualChat
        MediaViewer -->|Назад| GroupChat

---------
## 🔄 Архитектура и поток данных

### Схема взаимодействия компонентов

```mermaid
flowchart LR
    %% Стилизация узлов
    classDef ui fill:#FFE5E5,stroke:#EF4444,stroke-width:2px
    classDef vm fill:#E5F3FF,stroke:#3B82F6,stroke-width:2px
    classDef repo fill:#E5FFE5,stroke:#10B981,stroke-width:2px
    classDef data fill:#FFF5E5,stroke:#F59E0B,stroke-width:2px

    %% UI Layer
    subgraph UI [" UI Layer (View)"]
        Activity[ Activity/Fragment]:::ui
        Adapter[ RecyclerView Adapter]:::ui
        CustomView[ Custom View]:::ui
    end

    %% ViewModel Layer
    subgraph VM [" ViewModel Layer"]
        ViewModel[ ViewModel]:::vm
        LiveData[ LiveData/Observer]:::vm
        State[ UI State]:::vm
    end

    %% Repository Layer
    subgraph Repo [" Repository Layer"]
        Repository[ Repository]:::repo
        Cache[ Local Cache]:::repo
    end

    %% Data Layer
    subgraph Data [" Data Layer"]
        API[ Retrofit API]:::data
        Database[ Room Database]:::data
        Preferences[ SharedPreferences]:::data
        Backend[ Spring Boot Server]:::data
    end

    %% Потоки данных: UI → ViewModel
    Activity -->|1. User Action| ViewModel
    Adapter -->|1. Item Click| ViewModel
    CustomView -->|1. Input Changed| ViewModel

    %% Потоки данных: ViewModel → UI
    ViewModel -->|2. Observe LiveData| Activity
    ViewModel -->|2. Update State| Adapter
    LiveData -->|2. Auto Update| CustomView

    %% Потоки данных: ViewModel → Repository
    ViewModel -->|3. Request Data| Repository
    ViewModel -->|3. Save Data| Repository

    %% Потоки данных: Repository → Data Sources
    Repository -->|4a. Network First| API
    Repository -->|4b. Cache Fallback| Database
    Repository -->|4c. Settings| Preferences

    %% Потоки данных: Data Sources → Repository
    API -->|5a. JSON Response| Repository
    Database -->|5b. Cached Entities| Repository
    Preferences -->|5c. Stored Values| Repository

    %% Внешние вызовы
    API <-->|6. HTTP/HTTPS| Backend

    %% Легенда
    linkStyle 0,1,2 stroke:#EF4444,stroke-width:2px
    linkStyle 3,4,5 stroke:#3B82F6,stroke-width:2px
    linkStyle 6,7,8 stroke:#10B981,stroke-width:2px
    linkStyle 9,10,11 stroke:#F59E0B,stroke-width:2px
    linkStyle 12 stroke:#9CA3AF,stroke-width:2px,stroke-dasharray:5 5
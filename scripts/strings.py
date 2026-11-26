#!/usr/bin/env python3
"""Generates core/ui strings in English (default), Ukrainian and Polish from one table.

Usage: python3 scripts/strings.py
Every key must have all three languages; the script refuses to write otherwise.
"""
import os
import sys
from xml.sax.saxutils import escape

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "core", "ui", "src", "main", "res")

# key: (en, uk, pl)
S = {
    "app_name": ("Lirnyk", "Lirnyk", "Lirnyk"),
    # player
    "nothing_playing": ("Nothing is playing", "Нічого не грає", "Nic nie gra"),
    "pick_music_hint": ("Open the library and choose music", "Відкрийте бібліотеку і виберіть музику", "Otwórz bibliotekę i wybierz muzykę"),
    "unknown_artist": ("Unknown artist", "Невідомий виконавець", "Nieznany wykonawca"),
    "play": ("Play", "Грати", "Odtwórz"),
    "pause": ("Pause", "Пауза", "Pauza"),
    "next": ("Next", "Далі", "Następny"),
    "previous": ("Previous", "Назад", "Poprzedni"),
    "shuffle": ("Shuffle", "Перемішати", "Losowo"),
    "repeat": ("Repeat", "Повтор", "Powtarzaj"),
    "favorite": ("Favourite", "Обране", "Ulubione"),
    "track_unavailable": ("The track is on a drive that is not connected", "Трек на носії, який зараз не підключено", "Utwór jest na nośniku, który nie jest podłączony"),
    "waiting_for_media": ("Waiting for the USB drive or SD card…", "Чекаю на флешку або SD-карту…", "Czekam na pendrive lub kartę SD…"),
    "saved_track_missing": ("The track that was playing is no longer in the library. Choose music in the library.", "Трек, що грав, більше не знайдено в бібліотеці. Виберіть музику в бібліотеці.", "Utworu, który grał, nie ma już w bibliotece. Wybierz muzykę w bibliotece."),
    "playback_error": ("Cannot play this file", "Не вдається відтворити цей файл", "Nie można odtworzyć tego pliku"),
    # tiles
    "tile_library": ("Library", "Бібліотека", "Biblioteka"),
    "tile_sources": ("Sources", "Джерела", "Źródła"),
    "tile_check": ("Head unit check", "Перевірка магнітоли", "Test radia"),
    "tile_settings": ("Settings", "Налаштування", "Ustawienia"),
    "back": ("Back", "Назад", "Wstecz"),
    # library
    "lib_folders": ("Folders", "Папки", "Foldery"),
    "lib_albums": ("Albums", "Альбоми", "Albumy"),
    "lib_artists": ("Artists", "Виконавці", "Wykonawcy"),
    "lib_tracks": ("All tracks", "Усі треки", "Wszystkie"),
    "lib_favorites": ("Favourites", "Обране", "Ulubione"),
    "lib_search": ("Search", "Пошук", "Szukaj"),
    "lib_search_hint": ("Title, artist or album", "Назва, виконавець або альбом", "Tytuł, wykonawca lub album"),
    "lib_empty": ("No music found yet. Allow access to audio files or add a folder in Sources.", "Музики поки не знайдено. Дозвольте доступ до аудіофайлів або додайте папку в «Джерелах».", "Nie znaleziono muzyki. Zezwól na dostęp do plików audio lub dodaj folder w „Źródłach”."),
    "lib_play_all": ("Play all", "Грати все", "Odtwórz wszystko"),
    "lib_tracks_count": ("Tracks: %1$d", "Треків: %1$d", "Utworów: %1$d"),
    "lib_scanning": ("Scanning…", "Сканую…", "Skanowanie…"),
    # sources
    "src_title": ("Sources", "Джерела", "Źródła"),
    "src_permission": ("Allow access to audio files", "Дозволити доступ до аудіофайлів", "Zezwól na dostęp do plików audio"),
    "src_permission_body": ("Needed to list music in the head unit memory, on SD cards and USB drives that Android indexes.", "Потрібно, щоб показати музику з пам'яті магнітоли, SD-карт і флешок, які індексує Android.", "Potrzebne, aby pokazać muzykę z pamięci radia, kart SD i pendrive'ów indeksowanych przez Androida."),
    "src_add_folder": ("Add a music folder", "Додати папку з музикою", "Dodaj folder z muzyką"),
    "src_add_folder_body": ("Many head units do not index USB drives. Pick the drive or a folder on it once; Lirnyk will remember it.", "Багато магнітол не індексують флешки. Один раз виберіть флешку або папку на ній — Lirnyk її запам'ятає.", "Wiele radii nie indeksuje pendrive'ów. Wybierz raz nośnik lub folder — Lirnyk go zapamięta."),
    "src_rescan": ("Rescan now", "Пересканувати", "Skanuj ponownie"),
    "src_remove": ("Remove", "Прибрати", "Usuń"),
    "src_volumes": ("Drives", "Носії", "Nośniki"),
    "src_folders": ("Picked folders", "Вибрані папки", "Wybrane foldery"),
    "src_no_folders": ("No folders picked", "Папок не вибрано", "Nie wybrano folderów"),
    "src_no_picker": ("This head unit has no folder picker. Copy music to the internal Music folder or use a drive Android indexes.", "На цій магнітолі немає вибору папок. Скопіюйте музику в папку Music у пам'яті або використайте носій, який індексує Android.", "To radio nie ma wyboru folderów. Skopiuj muzykę do folderu Music lub użyj nośnika indeksowanego przez Androida."),
    "src_kind_internal": ("Internal memory", "Внутрішня пам'ять", "Pamięć wewnętrzna"),
    "src_kind_sd": ("SD card", "SD-карта", "Karta SD"),
    "src_kind_usb": ("USB drive", "USB-флешка", "Pendrive USB"),
    "src_kind_removable": ("Removable drive", "Знімний носій", "Nośnik wymienny"),
    "src_kind_folder": ("Picked folder", "Вибрана папка", "Wybrany folder"),
    "src_mounted": ("connected", "підключено", "podłączony"),
    "src_unmounted": ("not connected", "не підключено", "niepodłączony"),
    "src_tracks": ("Tracks: %1$d, available: %2$d", "Треків: %1$d, доступно: %2$d", "Utworów: %1$d, dostępnych: %2$d"),
    "src_other_players": ("Other players", "Інші плеєри", "Inne odtwarzacze"),
    "src_other_players_body": ("Controlling other players comes in a later stage, only through official Android connections. The head unit check lists which installed players offer one.", "Керування іншими плеєрами — на наступному етапі, лише через офіційні підключення Android. «Перевірка магнітоли» показує, які встановлені плеєри їх надають.", "Sterowanie innymi odtwarzaczami pojawi się później, tylko przez oficjalne połączenia Androida. Test radia pokazuje, które odtwarzacze je udostępniają."),
    # settings
    "set_title": ("Settings", "Налаштування", "Ustawienia"),
    "set_autoresume": ("Continue music after the head unit starts", "Продовжувати музику після запуску магнітоли", "Wznawiaj muzykę po uruchomieniu radia"),
    "set_autoresume_body": ("Only if music was playing when the car was switched off. Same track, same place.", "Лише якщо музика грала, коли авто вимкнули. Той самий трек, те саме місце.", "Tylko jeśli muzyka grała przy wyłączeniu auta. Ten sam utwór, to samo miejsce."),
    "set_theme": ("Theme", "Тема", "Motyw"),
    "set_theme_auto": ("Automatic", "Автоматично", "Automatycznie"),
    "set_theme_night": ("Night", "Ніч", "Noc"),
    "set_theme_day": ("Day", "День", "Dzień"),
    "set_keep_screen": ("Keep the screen on while music plays", "Не вимикати екран під час музики", "Nie wygaszaj ekranu podczas odtwarzania"),
    "set_notifications": ("Allow the playback notification", "Дозволити сповіщення відтворення", "Zezwól na powiadomienie odtwarzania"),
    "set_about": ("About", "Про застосунок", "O aplikacji"),
    "set_about_body": ("Lirnyk %1$s · works offline, no account, no internet permission, no ads.", "Lirnyk %1$s · працює офлайн, без акаунта, без дозволу на інтернет, без реклами.", "Lirnyk %1$s · działa offline, bez konta, bez uprawnienia do internetu, bez reklam."),
    # head unit check
    "chk_title": ("Head unit check", "Перевірка магнітоли", "Test radia"),
    "chk_intro": ("Press every steering-wheel button now. Then share the report with the team. It contains no track names or file paths.", "Натисніть зараз усі кнопки на кермі. Потім надішліть звіт команді. У ньому немає назв треків і шляхів до файлів.", "Naciśnij teraz wszystkie przyciski na kierownicy. Potem wyślij raport zespołowi. Nie zawiera nazw utworów ani ścieżek."),
    "chk_keys": ("Buttons received", "Отримані кнопки", "Odebrane przyciski"),
    "chk_no_keys": ("No buttons yet", "Кнопок поки немає", "Brak przycisków"),
    "chk_share": ("Share report", "Надіслати звіт", "Wyślij raport"),
    "chk_copy": ("Copy", "Копіювати", "Kopiuj"),
    "chk_copied": ("Report copied", "Звіт скопійовано", "Raport skopiowany"),
    "chk_players": ("Players with an official media connection", "Плеєри з офіційним медіапідключенням", "Odtwarzacze z oficjalnym połączeniem multimediów"),
    "chk_players_body": ("Apps on this head unit that offer Android's public media connection. Lirnyk will control other players only through such official connections (later stage). It does not ask for notification access.", "Застосунки на цій магнітолі, які надають публічне медіапідключення Android. Керувати іншими плеєрами Lirnyk буде лише через такі офіційні підключення (на наступному етапі). Доступу до сповіщень він не просить.", "Aplikacje na tym radiu udostępniające publiczne połączenie multimediów Androida. Lirnyk będzie sterować innymi odtwarzaczami tylko przez takie oficjalne połączenia (kolejny etap). Nie prosi o dostęp do powiadomień."),
    "chk_players_none": ("None found", "Не знайдено", "Nie znaleziono"),
}


# TASK 003: Spotify prototype (spotify/app). Separate table, separate module.
REMOTE_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "spotify", "app", "src", "main", "res")
REMOTE = {
    "remote_app_name": ("Lirnyk Remote", "Лірник Дистанційне", "Lirnyk Pilot"),
    "remote_prototype": ("Private prototype · Spotify App Remote · not for distribution", "Приватний прототип · Spotify App Remote · не для поширення", "Prywatny prototyp · Spotify App Remote · nie do dystrybucji"),
    "remote_connect": ("Connect Spotify", "Підключити Spotify", "Połącz Spotify"),
    "remote_reconnect": ("Connect again", "Підключити знову", "Połącz ponownie"),
    "remote_authorize": ("Authorize in Spotify", "Авторизувати в Spotify", "Autoryzuj w Spotify"),
    "remote_disconnect": ("Disconnect", "Відключити", "Rozłącz"),
    "remote_forget": ("Disconnect and delete data", "Відключити і видалити дані", "Rozłącz i usuń dane"),
    "remote_forget_done": ("Disconnected. Lirnyk Remote stores nothing about your account now. To revoke the authorization itself, use \"Revoke access\".", "Відключено. Lirnyk Дистанційне більше нічого не зберігає про ваш акаунт. Щоб відкликати саму авторизацію, натисніть «Відкликати доступ».", "Rozłączono. Aplikacja nie przechowuje już danych konta. Aby cofnąć samą autoryzację, użyj „Cofnij dostęp”."),
    "remote_revoke": ("Revoke access in your Spotify account", "Відкликати доступ в акаунті Spotify", "Cofnij dostęp w koncie Spotify"),
    "remote_open_spotify": ("Open Spotify", "Відкрити Spotify", "Otwórz Spotify"),
    "remote_status_not_set_up": ("Not connected. Tap \"Connect Spotify\": Spotify will ask you to allow control.", "Не підключено. Натисніть «Підключити Spotify» — Spotify попросить дозволити керування.", "Nie połączono. Dotknij „Połącz Spotify” — Spotify poprosi o zgodę."),
    "remote_status_connecting": ("Connecting to Spotify…", "Підключення до Spotify…", "Łączenie ze Spotify…"),
    "remote_status_connected": ("Connected to Spotify", "Підключено до Spotify", "Połączono ze Spotify"),
    "remote_status_disconnected": ("Disconnected (authorization kept)", "Відключено (авторизацію збережено)", "Rozłączono (autoryzacja zachowana)"),
    "remote_p_not_installed": ("Spotify is not installed on this device.", "Spotify не встановлено на цьому пристрої.", "Spotify nie jest zainstalowany."),
    "remote_p_not_authorized": ("Spotify needs your permission. Tap \"Authorize in Spotify\".", "Spotify потребує вашого дозволу. Натисніть «Авторизувати в Spotify».", "Spotify potrzebuje zgody. Dotknij „Autoryzuj w Spotify”."),
    "remote_p_auth_failed": ("Spotify refused this app. Check in the Spotify dashboard: package app.lirnyk.remote, the SHA-1 below, redirect URI app.lirnyk.remote://callback, and that your account is in the app's user list.", "Spotify відхилив застосунок. Перевірте в Spotify Dashboard: пакет app.lirnyk.remote, SHA-1 нижче, Redirect URI app.lirnyk.remote://callback і що ваш акаунт є у списку користувачів застосунку.", "Spotify odrzucił aplikację. Sprawdź w panelu Spotify: pakiet app.lirnyk.remote, SHA-1 poniżej, Redirect URI app.lirnyk.remote://callback i listę użytkowników."),
    "remote_p_not_logged_in": ("Log in to the Spotify app first.", "Спершу увійдіть у застосунок Spotify.", "Najpierw zaloguj się w Spotify."),
    "remote_p_offline": ("Spotify is offline. App Remote can connect only while Spotify is online.", "Spotify офлайн. App Remote підключається, лише коли Spotify онлайн.", "Spotify jest offline. Połączenie działa tylko online."),
    "remote_p_too_old": ("The Spotify app is too old. Update Spotify.", "Застосунок Spotify застарий. Оновіть Spotify.", "Aplikacja Spotify jest za stara. Zaktualizuj ją."),
    "remote_p_lost": ("Spotify closed the connection. Reconnecting…", "Spotify розірвав з'єднання. Підключаюся знову…", "Spotify zamknął połączenie. Ponowne łączenie…"),
    "remote_p_service": ("Android did not let Spotify start in the background. Open Spotify once, then connect again.", "Android не дозволив Spotify запуститися у фоні. Відкрийте Spotify, потім підключіться знову.", "Android nie pozwolił uruchomić Spotify w tle. Otwórz Spotify i połącz ponownie."),
    "remote_p_unknown": ("Connection failed: %1$s", "Не вдалося підключитися: %1$s", "Błąd połączenia: %1$s"),
    "remote_nothing": ("Nothing playing in Spotify", "У Spotify нічого не грає", "Nic nie gra w Spotify"),
    "remote_play": ("Play", "Грати", "Odtwórz"),
    "remote_pause": ("Pause", "Пауза", "Pauza"),
    "remote_next": ("Next", "Далі", "Następny"),
    "remote_previous": ("Previous", "Назад", "Poprzedni"),
    "remote_shuffle": ("Shuffle", "Перемішати", "Losowo"),
    "remote_repeat": ("Repeat", "Повтор", "Powtarzaj"),
    "remote_cover": ("Album cover", "Обкладинка", "Okładka"),
    "remote_check_title": ("App identity for the Spotify dashboard", "Дані застосунку для Spotify Dashboard", "Dane aplikacji dla panelu Spotify"),
    "remote_check_package": ("Package: %1$s", "Пакет: %1$s", "Pakiet: %1$s"),
    "remote_check_sha1": ("Signing SHA-1: %1$s", "SHA-1 підпису: %1$s", "SHA-1 podpisu: %1$s"),
    "remote_check_match": ("Matches the SHA-1 registered for Spotify", "Збігається з SHA-1, зареєстрованим для Spotify", "Zgodny z SHA-1 zarejestrowanym w Spotify"),
    "remote_check_mismatch": ("Does NOT match the expected SHA-1 %1$s: Spotify will refuse the connection", "НЕ збігається з очікуваним SHA-1 %1$s: Spotify відхилить підключення", "NIE zgadza się z oczekiwanym SHA-1 %1$s: Spotify odrzuci połączenie"),
    "remote_check_unknown": ("Expected SHA-1 not set in this build (run scripts/termux/setup-signing.sh)", "Очікуваний SHA-1 у цій збірці не задано (запустіть scripts/termux/setup-signing.sh)", "Brak oczekiwanego SHA-1 w tej wersji"),
    "remote_check_redirect": ("Redirect URI: %1$s", "Redirect URI: %1$s", "Redirect URI: %1$s"),
}


def write_table(table, root, lang_dir, idx, untranslatable=()):
    path = os.path.join(root, lang_dir, "strings.xml")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Generated by scripts/strings.py. Edit the table there, not this file. -->", "<resources>"]
    for key, vals in table.items():
        v = escape(vals[idx]).replace("'", "\\'")
        if '\\"' not in v:
            v = v.replace('"', '\\"')
        lines.append(f'    <string name="{key}">{v}</string>')
    lines.append("</resources>")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def write(lang_dir, idx):
    path = os.path.join(ROOT, lang_dir, "strings.xml")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Generated by scripts/strings.py. Edit the table there, not this file. -->", "<resources>"]
    for key, vals in S.items():
        v = escape(vals[idx]).replace("'", "\\'").replace('"', '\\"')
        attr = ' translatable="false"' if key == "app_name" and idx == 0 else ""
        if key == "app_name" and idx > 0:
            continue
        lines.append(f'    <string name="{key}"{attr}>{v}</string>')
    lines.append("</resources>")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    bad = [k for k, v in S.items() if len(v) != 3 or any(not x for x in v)]
    if bad:
        sys.exit("missing translations: " + ", ".join(bad))
    write("values", 0)
    write("values-uk", 1)
    write("values-pl", 2)
    bad = [k for k, v in REMOTE.items() if len(v) != 3 or any(not x for x in v)]
    if bad:
        sys.exit("missing remote translations: " + ", ".join(bad))
    for d, i in (("values", 0), ("values-uk", 1), ("values-pl", 2)):
        write_table(REMOTE, REMOTE_ROOT, d, i)
    print(f"{len(S)} strings × 3 languages; {len(REMOTE)} remote strings × 3")

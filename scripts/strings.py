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
    "src_other_players_body": ("Controlling Spotify and other players comes in the next stage. The head unit check already shows whether this unit lets Lirnyk see them.", "Керування Spotify та іншими плеєрами — на наступному етапі. «Перевірка магнітоли» вже показує, чи ця магнітола дозволяє Lirnyk їх бачити.", "Sterowanie Spotify i innymi odtwarzaczami pojawi się w kolejnym etapie. Test radia już pokazuje, czy radio pozwala je widzieć."),
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
    "chk_sessions_grant": ("Let Lirnyk see other players", "Дозволити Lirnyk бачити інші плеєри", "Pozwól Lirnyk widzieć inne odtwarzacze"),
    "chk_sessions_body": ("Android shows other players' controls only to apps with notification access. Lirnyk does not read or store notifications; it only lists active players. Optional.", "Android показує керування іншими плеєрами лише застосункам із доступом до сповіщень. Lirnyk не читає і не зберігає сповіщення, а лише бачить активні плеєри. Необов'язково.", "Android pokazuje sterowanie innymi odtwarzaczami tylko aplikacjom z dostępem do powiadomień. Lirnyk nie czyta ani nie zapisuje powiadomień. Opcjonalne."),
    "chk_restricted_hint": ("If Android says the setting is restricted (app installed from a file): open Lirnyk in system App info, menu ⋮ → Allow restricted settings, then try again.", "Якщо Android пише, що налаштування обмежене (застосунок встановлено з файлу): відкрийте Lirnyk у системних «Відомостях про застосунок», меню ⋮ → «Дозволити обмежені налаштування», і спробуйте ще раз.", "Jeśli Android pisze, że ustawienie jest ograniczone (instalacja z pliku): otwórz Lirnyk w Informacjach o aplikacji, menu ⋮ → Zezwól na ograniczone ustawienia i spróbuj ponownie."),
    "chk_no_settings_screen": ("This head unit has no notification access screen", "На цій магнітолі немає екрана доступу до сповіщень", "To radio nie ma ekranu dostępu do powiadomień"),
}


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
    print(f"{len(S)} strings × 3 languages")

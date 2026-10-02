# Sound sources

Every physical shot and action sound shipped by this mod is cut from recordings of real objects,
taken from freesound.org under the Creative Commons Zero (CC0 1.0) public-domain dedication, which
permits use, modification and redistribution without attribution. The recordists are credited here
anyway, because they deserve it. The files in this directory are the recordings as downloaded
(Freesound's high-quality previews; format noted below); `build.py` cuts, re-times, mixes and normalizes them
into `src/main/resources/assets/rangedweaponsmod/sounds/` (see `RECORDINGS` there for the edits).

| File | Title | Recordist | Freesound page | License |
|---|---|---|---|---|
| `427592-9mm-pistol-shot.ogg` | 9mm pistol shot | michorvath | https://freesound.org/people/michorvath/sounds/427592/ | CC0 1.0 |
| `427596-ar15-rifle-shot.ogg` | AR15 rifle shot | michorvath | https://freesound.org/people/michorvath/sounds/427596/ | CC0 1.0 |
| `427595-20-gauge-shotgun-shot.ogg` | 20 gauge shotgun gunshot | michorvath | https://freesound.org/people/michorvath/sounds/427595/ | CC0 1.0 |
| `159710-mossberg-500a-shot-and-pump.ogg` | Mossberg 500A - 1 shot and pump | AnthonyChan0 | https://freesound.org/people/AnthonyChan0/sounds/159710/ | CC0 1.0 |
| `854641-m240-single-shot.ogg` | M240 Machine Gun Single Shot | qubodup (from U.S. Army V Corps B-roll, public domain) | https://freesound.org/people/qubodup/sounds/854641/ | CC0 1.0 |
| `712577-hk-g3-shots.ogg` | Heckler & Koch G3 | areniporgen | https://freesound.org/people/areniporgen/sounds/712577/ | CC0 1.0 |
| `802673-mauser-98k-bolt.ogg` | Mauser 98k - bolt-action mechanism sound | Walking.With.Microphones | https://freesound.org/people/Walking.With.Microphones/sounds/802673/ | CC0 1.0 |
| `815476-m110-shot-echo.ogg` | Sniper Shot Echoey | qubodup | https://freesound.org/people/qubodup/sounds/815476/ | CC0 1.0 |
| `725402-rifle-dry-fire.ogg` | A rifle being dry fired once | serøutōnin--deprivəd | https://freesound.org/people/ser%C3%B8ut%C5%8Dnin--depriv%C9%99d/sounds/725402/ | CC0 1.0 |
| `104407-1911-magazine-out.ogg` | Mag remove.wav | Nanashi | https://freesound.org/people/Nanashi/sounds/104407/ | CC0 1.0 |
| `427593-9mm-pistol-load-and-chamber.ogg` | 9mm pistol load and chamber | michorvath | https://freesound.org/people/michorvath/sounds/427593/ | CC0 1.0 |

## Launcher — 2.11.0

The launcher uses recordings for physical sounds and original synthesis only for its electronic
seeker (the explicit D-0028 exception to D-0012). The following files are the original recordists'
Freesound high-quality **MP3 previews**, downloaded unchanged with TLS verification on 2026-10-02.
Each individual sound page was read and its CC0 dedication verified. These are not lossless masters.

| File | Title | Recordist | Freesound page | License |
|---|---|---|---|---|
| `182794-rocket-launch.mp3` | Rocket Launch.flac | qubodup; extracted from a US Government agency's public-domain footage | https://freesound.org/people/qubodup/sounds/182794/ | CC0 1.0 |
| `453408-blowtorch.mp3` | blowtorch light and run flame good detail on off2.flac | kyles | https://freesound.org/people/kyles/sounds/453408/ | CC0 1.0 |
| `14167-metal-slide.mp3` | 19.wav (Metal slide) | adcbicycle | https://freesound.org/people/adcbicycle/sounds/14167/ | CC0 1.0 |

Exact downloaded previews and SHA-256:

- `https://cdn.freesound.org/previews/182/182794_71257-hq.mp3`:
  `6853bef08fe98fd3682bf0622f23693b2fb05dd8a02a2c69804654bce8b49d16`
- `https://cdn.freesound.org/previews/453/453408_612689-hq.mp3`:
  `68a661a736753b4486a2ab83e2803e79d0380151e717fa5158344e3f2040e418`
- `https://cdn.freesound.org/previews/14/14167_33634-hq.mp3`:
  `34895c601eacc2b2b470d9f70e210983d2c73ada03dbbc2d33f434467a4f218f`

`launcher_sound.py`, called by `build.py sounds`, records the edits exactly. All cuts use seconds
of the decoded source at its original speed, resampled to 44.1 kHz mono:

| Event | Edit |
|---|---|
| `launcher_fire` | Rocket launch 0.575–2.475 s; 2 ms attack fade, 400 ms tail fade; peak .95 before Vorbis. |
| `launcher_reload` | Existing Nanashi magazine recording 0.19–0.49 s at 0.04 s; adcbicycle slide 0–0.80 s at 0.38 s; existing Walking.With.Microphones bolt 1.20–1.70 s at 1.70 s. Faded, mixed, peak .83, total 2.35 s inside the 2.5 s reload. |
| `rocket_motor` | Blowtorch 18–21 s, DC removed, tail/head crossfaded over 250 ms at equal power; rotated splice gives a continuous 2.75 s loop, peak .72. |
| `rocket_dud` | Magazine impact 0.19–0.49 s plus blowtorch tail 43.8–44.5 s at 0.05 s; fades and peak .68; 0.78 s total. |
| `seeker_growl` | Original periodic 220 Hz carrier with 55 Hz frequency modulation and 8 Hz amplitude modulation; one-second loop, peak .28. Runtime bends playback pitch from .8 to 1.6. |
| `seeker_lock` | Original steady 660 Hz tone plus a quiet second harmonic; one-second loop, peak below .24. |
| `seeker_lost` | Original 300 ms fall from 660 to 180 Hz, faded at both ends, peak .24. |

The physical sounds add no synthesized gun report, reverb, or noise. The three loop files have
no fade-to-silence at their seams. Every exported Vorbis file is decoded and checked against
D-0017's .98 peak ceiling. Measurements establish headroom; Rusty's listening approval is separate.

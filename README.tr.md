# SurvivalKit

[English](README.md) · **Türkçe**

Minecraft hayatta kalma modu için istemci taraflı bir [Fabric](https://fabricmc.net/) modu: yemek, totem, alet, silah, köprü kurma, ağaç ve damar kırma, dünya indirme. Hepsi oyun içinde tek bir menüden yönetilir.

Yalnızca istemciye kurulur, sunucuya bir şey kurmak gerekmez. Arayüz İngilizce ve Türkçedir.

## İndirme

Jar dosyasını [Releases](https://github.com/ahmetelcin/SurvivalKit/releases) sayfasından alın.

| Minecraft | SurvivalKit | Fabric Loader | Java |
|---|---|---|---|
| 26.3 | [0.13.1](https://github.com/ahmetelcin/SurvivalKit/releases/tag/v0.13.1-mc26.3) | 0.19.5 veya üzeri | 25 |
| 26.2 | [0.12.1](https://github.com/ahmetelcin/SurvivalKit/releases/tag/v0.12.1-mc26.2) | 0.19.3 veya üzeri | 25 |

## Kurulum

1. Minecraft sürümünüz için [Fabric Loader](https://fabricmc.net/use/) kurun.
2. [Fabric API](https://modrinth.com/mod/fabric-api) ve SurvivalKit jar dosyasını `mods` klasörüne koyun.
3. Oyunu açın, menü için **Sağ Shift**'e basın. Bu tuşu *Seçenekler → Kontroller → Tuş Atamaları* altından değiştirebilirsiniz.

## Modüller

| Modül | Ne yapar |
|---|---|
| **Otomatik Yeme** | Açlık eşiğin altına düşünce yer, sonra hotbar'ı bulduğu gibi bırakır. Yemek tercihleri, kara liste ve altın elma kuralları vardır. |
| **Otomatik Totem** | Diğer elde ölümsüzlük totemi tutar, kullanılınca yenisini koyar. |
| **Otomatik Alet** | Blok kırmaya başlayınca en iyi aleti ele alır, bırakınca eski eşyaya döner. |
| **Alet Seçici** | Bloğa orta tıklayınca o blok için en iyi aleti seçer. |
| **Oto Silah** | Hedefe göre en iyi silahı seçer: ölümsüzlere Smite, örümceklere Bane of Arthropods, gerisine Keskinlik. Otomatik vurabilir, moblar yaklaşırken silahı hazırlayabilir. |
| **İskele** | Boşluğa yürürken bir sonraki adımın altına blok koyar. Y kilidi, rampa, köprü genişliği, kenarda durma ve meşale seçenekleri vardır. Bakışınız hiç döndürülmez. |
| **Damar Kırıcı** | Bütün bir ağacı ya da cevher damarını sunucunun kendi komutlarıyla tek seferde kırar. `/setblock` yetkisi yoksa hiçbir şey yapmaz. |
| **Yapı Modu** | Değnekle alan seçer. Damar Kırıcı ve İskele bu alanın içinde kalır. Alan her sunucu ve boyut için ayrı saklanır. |
| **Dünya İndirici** | Sunucuda gezdiğiniz chunk'ları, konteyner ve varlıklarla birlikte tek oyunculuda açılabilen bir dünya olarak kaydeder. Sunucu eski World Downloader izin kanalıyla yasaklarsa durur. |
| **HUD** | Açık modülleri ve durumlarını ekranda kutularla gösterir. Kutular sürükle-bırak düzenleyiciyle yerleştirilir. |

**Profiller ve hazır ayarlar**

- **Profiller:** Ayrı düzenler tutabilir, adres kurallarıyla sunucuya göre otomatik geçebilirsiniz.
- **Güvenli profil:** Tüm modüllerin kapalı olduğu yerleşik profil.
- **Hazır ayarlar:** Her modülün yerleşik hazır ayarları vardır, kendinizinkini de kaydedebilirsiniz.
- **Ayarlar:** Her ayarın açıklaması vardır, menü araması modülleri ve ayarları kapsar.

Tüm ayarlar ve komutlar [kullanım kılavuzunda](docs/KULLANIM.md), sürüm geçmişi [değişiklik günlüğünde](CHANGELOG.md).

## Sunucularda kullanım

**Modu bir sunucuda kullanmadan önce o sunucunun kurallarına bakın. Birçok sunucu Oto Silah, İskele ya da dünya indirme gibi otomasyonları yasaklar. Modun ne yapıp ne yapmadığından bağımsız olarak ban yiyebilirsiniz.**

SurvivalKit anticheat atlatmaz. Sahte hareket ya da düşüş paketi göndermez, paket geciktirmez; modüller yalnızca normal bir oyuncunun yapabildiği eylemleri yapar. Bu, modun tespit edilemez ya da serbest olduğu anlamına gelmez.

*Güvenli* profil ve sunucuya göre profil kuralları, modüllerin izin verilmeyen yerlerde kapalı kalmasına yardım eder. Kutudan çıktığında hiçbir sunucu için kural yoktur.

## Kaynaktan derleme

JDK 25 gerekir.

```sh
./gradlew build
```

Jar `build/libs/` altına yazılır.

Karar veren mantık, oyun olmadan test edilen düz Java sınıflarındadır; Minecraft ile ince bir katman konuşur. Bu katman, gerçek tıklama ve tuş basışlarıyla çalışan client gametest'lerle sınanır. Modda mixin yoktur.

```sh
./gradlew test runClientGameTestParallel
```

## Lisans

[MIT](LICENSE)

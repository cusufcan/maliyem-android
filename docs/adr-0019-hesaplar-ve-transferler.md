# ADR-0019 · Hesaplar ve transferler

- **Durum** Uygulandı (2026-11-26)
- **Bağlam** Birden çok hesabın bakiyesi izlenebilmeli ve mağazadaki sürümden gelen işlem geçmişi korunmalı.
- **Karar** Room v1 → v2 geçişi tek bir `cash` hesabı ekler ve mevcut işlemleri bu hesaba bağlar. İşlem kimlikleri, kategori bağlantıları, tarihler, notlar ve otomatik kimlik sayacı korunur. Yeni kurulum ve v1 yedeğinden geri yükleme aynı hazır hesabı kullanır.
- **Bakiye** Açılış bakiyesine tüm gelir ve gelen transferler eklenir, giderler ve giden transferler çıkarılır. Transferler ayrı tabloda kaldığı için gelir, gider, aylık net ve kategori sorguları aynı işlem tablosunu okumayı sürdürür. Transferin her iki ucu hesap silmeye karşı dış anahtarla korunur.
- **Arşiv** Geçmişi olan hesap arşivlenebilir. Arşivli hesap var olan kayıtta kalabilir ve yeni seçimlerde listelenmez. Varsayılan hesap silinemez. En az bir hesap etkin kalır. Özet kartındaki toplam etkin hesapların bakiyelerini kapsar.
- **Yedek** JSON v2 hesapları, transferleri ve işlemlerin hesap bağlantısını ekler. Geri yükleme dört tabloyu tek Room işleminde değiştirir. CSV hesabı ayrı sütunda yazar, transferin iki ucunu okla gösterir.

# ADR-0024 · Başlatıcıdan hızlı kayıt

- **Durum** Uygulandı (2026-11-26)
- **Karar** Başlatıcı kısayolları ile Glance widget'ının iki düğmesi, aynı gelir ve gider bağlantılarını `MainActivity`'ye yollar. Statik kısayolun Android tarafından verilen görev temizleme bayrağı, görev bağı boş yönlendiricide kalır. Ana etkinlik, bekleyen isteği mevcut Yeni kayıt alt sayfasına bir kez aktarır. Döndürmede form taslağı korunur.
- **Toplamlar** Widget takvim ayının netini gösterir. Alan büyüdüğünde gider ve gelir de görünür. Hilt giriş noktası mevcut tarih aralığı sorgusuna ulaşır. Eğilim hesabı ve Money biçimleyicisi yeniden kullanılır. Transferler aylık rakamlara girmez.
- **Yenileme** Kayıt yazımı, tekrarlayan kayıt üretimi ve yedek geri yükleme, Room yazımı tamamlanınca arka planda Glance güncellemesi ister. Açık Glance oturumu Room ve ay değişimini izler. Android'in günlük widget güncellemesi, uygulama açılmadan ayın değişmesini de kapsar. Room şeması ve JSON biçimi aynı sürümde kalır.

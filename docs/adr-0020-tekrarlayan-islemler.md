# ADR-0020 · Tekrarlayan işlemler

- **Durum** Uygulandı (2026-11-26)
- **Bağlam** Düzenli gelir ve giderler günü geldiğinde eklenebilmeli. Play sürümündeki kayıtlar, hesaplar ve transferler korunmalı.
- **Karar** Room v2 → v3, `recurring_rules` tablosunu oluşturur ve işlem tablosuna null olabilen, indeksli `recurringRuleId` sütununu ekler. Mevcut satırlar ve kimlik sayaçları yerinde kalır. Kural silinince bağlantı boşalır ve geçmiş işlemler korunur.
- **Takvim** Ay ve yıl tekrarları başlangıç gününe bağlıdır. Kısa ayda son gün kullanılır. Uygulama öne geldiğinde (`MainActivity.onStart`), kural eklenince ve düzenleme veya sürdürme sonrasında vadesi gelen günler işlenir. Bir çalıştırmada kural başına en çok 400 kayıt eklenir. Kayıtlar ve sıradaki tarih aynı Room işleminde yazılır. Sürdürme aradaki vadeleri tamamlar. Sıklık değişikliği sıradaki tarihten itibaren yeni takvime hizalanır.
- **Bağlantılar** Kategori silinirken işlemleri ve kuralları aynı türdeki “Diğer” kategorisine tek işlemde taşırız. Bağlı kuralı olan hesap silinemez. Hesabı arşivlemek var olan kuralın çalışmasını sürdürür.
- **Yedek** JSON v3 kural, sıradaki tarih ve işlem bağlantılarını saklar. V1/v2 dosyaları kuralsız geri yüklenir. CSV sütunları aynıdır.

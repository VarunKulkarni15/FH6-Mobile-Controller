using System;
using System.IO;
using System.Windows.Media.Imaging;
using QRCoder;

namespace WheelPad.App
{
    public static class QrCodeHelper
    {
        public static BitmapImage GenerateQrCode(string payload)
        {
            using var generator = new QRCodeGenerator();
            using var data = generator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.M);
            using var qrCode = new PngByteQRCode(data);
            byte[] bytes = qrCode.GetGraphic(12, new byte[] { 255, 255, 255 }, new byte[] { 12, 16, 23 });

            var bmp = new BitmapImage();
            using (var ms = new MemoryStream(bytes))
            {
                ms.Position = 0;
                bmp.BeginInit();
                bmp.CreateOptions = BitmapCreateOptions.PreservePixelFormat;
                bmp.CacheOption = BitmapCacheOption.OnLoad;
                bmp.StreamSource = ms;
                bmp.EndInit();
            }
            bmp.Freeze();
            return bmp;
        }
    }
}


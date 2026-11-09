package com.rutaexpress.parcel;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Etiquetas de 100 × 150 mm con el código QR del paquete, una por página. */
@Service
class LabelService {

    private static final Rectangle LABEL_SIZE = new Rectangle(283, 425); // 100 × 150 mm en puntos
    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font CODE = new Font(Font.COURIER, 14, Font.BOLD);
    private static final Font BODY = new Font(Font.HELVETICA, 11);
    private static final Font SMALL = new Font(Font.HELVETICA, 8);

    byte[] render(List<Parcel> parcels) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(LABEL_SIZE, 18, 18, 18, 18);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            boolean first = true;
            for (Parcel parcel : parcels) {
                if (!first) {
                    document.newPage();
                }
                first = false;
                writeLabel(document, parcel);
            }
            document.close();
        } catch (DocumentException | IOException | WriterException e) {
            throw new IllegalStateException("No se pudo generar la etiqueta", e);
        }
        return out.toByteArray();
    }

    private void writeLabel(Document document, Parcel parcel) throws DocumentException, IOException, WriterException {
        Paragraph brand = new Paragraph("RutaExpress", TITLE);
        brand.setAlignment(Element.ALIGN_CENTER);
        document.add(brand);

        Image qr = Image.getInstance(qrImage(parcel.getTrackingCode()), null);
        qr.scaleAbsolute(170, 170);
        qr.setAlignment(Element.ALIGN_CENTER);
        document.add(qr);

        Paragraph code = new Paragraph(parcel.getTrackingCode(), CODE);
        code.setAlignment(Element.ALIGN_CENTER);
        document.add(code);

        document.add(new Paragraph(" ", SMALL));
        document.add(new Paragraph(parcel.getRecipient().getName(), BODY));
        document.add(new Paragraph(parcel.getAddress().oneLine(), BODY));
        if (parcel.getWindowStart() != null && parcel.getWindowEnd() != null) {
            document.add(new Paragraph("Franja: " + parcel.getWindowStart() + "–" + parcel.getWindowEnd(), BODY));
        }
        document.add(new Paragraph("Peso: " + parcel.getWeightKg() + " kg", SMALL));
    }

    private static BufferedImage qrImage(String content) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 400, 400,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
        return MatrixToImageWriter.toBufferedImage(matrix);
    }
}

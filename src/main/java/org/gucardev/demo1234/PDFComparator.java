package org.gucardev.demo1234;


import com.github.difflib.DiffUtils;
import com.github.difflib.patch.*;
import com.github.difflib.patch.Chunk;
import com.itextpdf.kernel.pdf.canvas.parser.listener.TextChunk;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.*;

import java.io.*;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PDFComparator {

    public static void comparePDFs(InputStream pdf1InputStream, InputStream pdf2InputStream, OutputStream outputStream,boolean isMultiple) throws IOException, DocumentException {
        PdfReader reader1 = new PdfReader(pdf1InputStream);
        PdfReader reader2 = new PdfReader(pdf2InputStream);

        // Initialize the document with a default page size
        Rectangle defaultPageSize = PageSize.A4;
        Document document = new Document(defaultPageSize);
        PdfWriter writer = PdfWriter.getInstance(document, outputStream);
        document.open();
        PdfContentByte cb = writer.getDirectContent();

        int totalPages = Math.max(reader1.getNumberOfPages(), reader2.getNumberOfPages());

        for (int i = 1; i <= totalPages; i++) {

            // Get page sizes for the current page
            Rectangle pageSize1 = i <= reader1.getNumberOfPages() ? reader1.getPageSize(i) : null;
            Rectangle pageSize2 = i <= reader2.getNumberOfPages() ? reader2.getPageSize(i) : null;

            // Determine the combined page size
            float width1 = pageSize1 != null ? pageSize1.getWidth() : 0;
            float width2 = pageSize2 != null ? pageSize2.getWidth() : 0;
            float combinedWidth = isMultiple ? width1 + width2 : width2;
            float height1 = pageSize1 != null ? pageSize1.getHeight() : 0;
            float height2 = pageSize2 != null ? pageSize2.getHeight() : 0;
            float combinedHeight = Math.max(height1, height2);

            if (combinedWidth == 0 || combinedHeight == 0) {
                // Skip if both pages are missing
                continue;
            }

            Rectangle combinedPageSize = new Rectangle(combinedWidth, combinedHeight);
            document.setPageSize(combinedPageSize);
            document.newPage();

            PdfImportedPage page1 = null;
            PdfImportedPage page2 = null;

            if (i <= reader1.getNumberOfPages()) {
                page1 = writer.getImportedPage(reader1, i);
                if (isMultiple) {
                    cb.addTemplate(page1, 0, 0);
                }
            }

            if (i <= reader2.getNumberOfPages()) {
                page2 = writer.getImportedPage(reader2, i);
                cb.addTemplate(page2, isMultiple ? width1 : 0, 0); // Only add the second PDF if isMultiple is false
            }

            // Check for new or removed pages and highlight entire page accordingly
            if (page1 == null && page2 != null) {
                Rectangle rect = new Rectangle(0, 0, width2, height2);
                highlightEntirePage(cb, rect, BaseColor.GREEN, isMultiple ? width1 : 0);
                continue;
            } else if (page1 != null && page2 == null && isMultiple) {
                Rectangle rect = new Rectangle(0, 0, width1, height1);
                highlightEntirePage(cb, rect, BaseColor.RED, 0);
                continue;
            }

            List<TextChunk> words1 = i <= reader1.getNumberOfPages() ? extractWords(reader1, i) : new ArrayList<>();
            List<TextChunk> words2 = i <= reader2.getNumberOfPages() ? extractWords(reader2, i) : new ArrayList<>();

            List<String> texts1 = new ArrayList<>();
            for (TextChunk word : words1) {
                texts1.add(word.text);
            }
            List<String> texts2 = new ArrayList<>();
            for (TextChunk word : words2) {
                texts2.add(word.text);
            }

            Patch<String> textPatch = DiffUtils.diff(texts1, texts2);

            for (AbstractDelta<String> delta : textPatch.getDeltas()) {
                Chunk<String> source = delta.getSource();
                Chunk<String> target = delta.getTarget();

                if (isMultiple && (delta.getType() == DeltaType.DELETE || delta.getType() == DeltaType.CHANGE)) {
                    for (int j = source.getPosition(); j < source.getPosition() + source.size(); j++) {
                        if (j < words1.size()) {
                            TextChunk word = words1.get(j);
                            drawRectangle(cb, word.rectangle, BaseColor.RED, 0);
                        }
                    }
                }

                if (delta.getType() == DeltaType.INSERT || delta.getType() == DeltaType.CHANGE) {
                    for (int j = target.getPosition(); j < target.getPosition() + target.size(); j++) {
                        if (j < words2.size()) {
                            TextChunk word = words2.get(j);
                            drawRectangle(cb, word.rectangle, BaseColor.GREEN, isMultiple ? width1 : 0);
                        }
                    }
                }
            }

            List<ImageChunk> images1 = i <= reader1.getNumberOfPages() ? extractImages(reader1, i) : new ArrayList<>();
            List<ImageChunk> images2 = i <= reader2.getNumberOfPages() ? extractImages(reader2, i) : new ArrayList<>();

            List<String> imageIds1 = new ArrayList<>();
            for (ImageChunk image : images1) {
                imageIds1.add(image.getIdentifier());
            }
            List<String> imageIds2 = new ArrayList<>();
            for (ImageChunk image : images2) {
                imageIds2.add(image.getIdentifier());
            }

            Patch<String> imagePatch = DiffUtils.diff(imageIds1, imageIds2);

            for (AbstractDelta<String> delta : imagePatch.getDeltas()) {
                Chunk<String> source = delta.getSource();
                Chunk<String> target = delta.getTarget();
                if (isMultiple && (delta.getType() == DeltaType.DELETE || delta.getType() == DeltaType.CHANGE)) {
                    for (int j = source.getPosition(); j < source.getPosition() + source.size(); j++) {
                        if (j < images1.size()) {
                            ImageChunk image = images1.get(j);
                            highlightRectangle(cb, image.rectangle, BaseColor.RED, 0);
                        }
                    }
                }
                if (delta.getType() == DeltaType.INSERT || delta.getType() == DeltaType.CHANGE) {
                    for (int j = target.getPosition(); j < target.getPosition() + target.size(); j++) {
                        if (j < images2.size()) {
                            ImageChunk image = images2.get(j);
                            highlightRectangle(cb, image.rectangle, BaseColor.GREEN, isMultiple ? width1 : 0);
                        }
                    }
                }
            }
        }

        document.close();
        reader1.close();
        reader2.close();
    }

    public static void highlightEntirePage(PdfContentByte cb, Rectangle rect, BaseColor color, float xOffset) {
        cb.saveState();
        PdfGState gs = new PdfGState();
        gs.setFillOpacity(0.2f); // Adjust opacity as needed
        cb.setGState(gs);
        cb.setColorFill(color);
        cb.rectangle(rect.getLeft() + xOffset, rect.getBottom(), rect.getWidth(), rect.getHeight());
        cb.fill();
        cb.restoreState();
    }

    // Method to extract words from a PDF page
    public static List<TextChunk> extractWords(PdfReader reader, int pageNum) throws IOException {
        List<TextChunk> words = new ArrayList<>();
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);

        parser.processContent(pageNum, new RenderListener() {
            public void beginTextBlock() {
            }

            public void endTextBlock() {
            }

            public void renderText(TextRenderInfo renderInfo) {
                String text = renderInfo.getText();
                // Split the text into words using space as delimiter
                String[] wordArray = text.split("\\s+");
                int wordIndex = 0;
                List<TextRenderInfo> characterRenderInfos = renderInfo.getCharacterRenderInfos();
                for (String word : wordArray) {
                    if (!word.trim().isEmpty()) {
                        if (wordIndex < characterRenderInfos.size()) {
                            // Get the bounding rectangle for the word
                            TextRenderInfo firstChar = characterRenderInfos.get(wordIndex);
                            int lastCharIndex = wordIndex + word.length() - 1;
                            if (lastCharIndex >= characterRenderInfos.size()) {
                                lastCharIndex = characterRenderInfos.size() - 1;
                            }
                            TextRenderInfo lastChar = characterRenderInfos.get(lastCharIndex);

                            Vector start = firstChar.getBaseline().getStartPoint();
                            Vector end = lastChar.getAscentLine().getEndPoint();

                            float minX = start.get(Vector.I1);
                            float minY = start.get(Vector.I2);
                            float maxX = end.get(Vector.I1);
                            float maxY = end.get(Vector.I2);

                            Rectangle rect = new Rectangle(minX, minY, maxX, maxY);
                            TextChunk textChunk = new TextChunk(word, rect);
                            words.add(textChunk);

                            wordIndex = lastCharIndex + 1;
                        }
                    } else {
                        wordIndex++;
                    }
                }
            }

            public void renderImage(ImageRenderInfo renderInfo) {
            }
        });
        return words;
    }

    // Method to extract images from a PDF page
    public static List<ImageChunk> extractImages(PdfReader reader, int pageNum) throws IOException {
        List<ImageChunk> images = new ArrayList<>();
        PdfReaderContentParser parser = new PdfReaderContentParser(reader);

        parser.processContent(pageNum, new RenderListener() {
            public void beginTextBlock() {
            }

            public void endTextBlock() {
            }

            public void renderText(TextRenderInfo renderInfo) {
            }

            public void renderImage(ImageRenderInfo renderInfo) {
                try {
                    PdfImageObject image = renderInfo.getImage();
                    if (image == null) {
                        return;
                    }
                    // Create a hash of the image content to use as an identifier
                    byte[] imageBytes = image.getImageAsBytes();
                    String imageHash = hashBytes(imageBytes);

                    // Get the position of the image
                    Matrix ctm = renderInfo.getImageCTM();

                    // Transform the image corners to user space
                    Vector[] corners = new Vector[4];
                    corners[0] = new Vector(0, 0, 1).cross(ctm);
                    corners[1] = new Vector(0, 1, 1).cross(ctm);
                    corners[2] = new Vector(1, 1, 1).cross(ctm);
                    corners[3] = new Vector(1, 0, 1).cross(ctm);

                    // Now find the min and max x and y coordinates
                    float minX = Float.MAX_VALUE;
                    float minY = Float.MAX_VALUE;
                    float maxX = Float.MIN_VALUE;
                    float maxY = Float.MIN_VALUE;

                    for (Vector corner : corners) {
                        float x = corner.get(Vector.I1);
                        float y = corner.get(Vector.I2);
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }

                    Rectangle rect = new Rectangle(minX, minY, maxX, maxY);
                    ImageChunk imageChunk = new ImageChunk(imageHash, rect);
                    images.add(imageChunk);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        return images;
    }

    // Method to draw a filled rectangle with transparency for text highlighting
    public static void drawRectangle(PdfContentByte cb, Rectangle rect, BaseColor color, float xOffset) {
        cb.saveState();
        // Set the transparency
        PdfGState gs = new PdfGState();
        gs.setFillOpacity(0.3f); // 30% transparent
        cb.setGState(gs);

        cb.setColorFill(color);
        cb.rectangle(rect.getLeft() + xOffset, rect.getBottom(), rect.getWidth(), rect.getHeight());
        cb.fill();
        cb.restoreState();
    }

    // Method to highlight a rectangle (uses fill with transparency) for images
    public static void highlightRectangle(PdfContentByte cb, Rectangle rect, BaseColor color, float xOffset) {
        cb.saveState();
        // Set the transparency
        PdfGState gs = new PdfGState();
        gs.setFillOpacity(0.3f); // 30% transparent
        cb.setGState(gs);

        cb.setColorFill(color);
        cb.rectangle(rect.getLeft() + xOffset, rect.getBottom(), rect.getWidth(), rect.getHeight());
        cb.fill();
        cb.restoreState();
    }

    // Method to hash byte array
    public static String hashBytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(data);
            // Convert byte array into signum representation
            BigInteger number = new BigInteger(1, hash);
            // Convert message digest into hex value
            StringBuilder hexString = new StringBuilder(number.toString(16));
            // Pad with leading zeros
            while (hexString.length() < 32) {
                hexString.insert(0, '0');
            }
            return hexString.toString();
        } catch (Exception e) {
            return Arrays.toString(data); // Fallback
        }
    }

    // Class to represent a text chunk with position
    static class TextChunk {
        public String text;
        public Rectangle rectangle;

        public TextChunk(String text, Rectangle rectangle) {
            this.text = text;
            this.rectangle = rectangle;
        }
    }

    // Class to represent an image chunk with position
    static class ImageChunk {
        public String imageHash;
        public Rectangle rectangle;

        public ImageChunk(String imageHash, Rectangle rectangle) {
            this.imageHash = imageHash;
            this.rectangle = rectangle;
        }

        public String getIdentifier() {
            return "image:" + imageHash;
        }
    }
}

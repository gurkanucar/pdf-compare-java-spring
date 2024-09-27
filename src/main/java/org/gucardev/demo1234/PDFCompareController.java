package org.gucardev.demo1234;


import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class PDFCompareController {

    @PostMapping(value = "/compare_pdfs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StreamingResponseBody> comparePDFs(
            @RequestParam("pdf1") MultipartFile pdf1,
            @RequestParam("pdf2") MultipartFile pdf2, @RequestParam(name = "isMultiple", defaultValue = "false", required = false) boolean isMultiple) {

        StreamingResponseBody stream = out -> {
            try {
                PDFComparator.comparePDFs(
                        pdf1.getInputStream(),
                        pdf2.getInputStream(),
                        out,
                        isMultiple);
            } catch (Exception e) {
                e.printStackTrace();
                // Handle exceptions as needed
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"compared.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(stream);
    }
}

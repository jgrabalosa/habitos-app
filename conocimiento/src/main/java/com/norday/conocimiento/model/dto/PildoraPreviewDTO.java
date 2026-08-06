package com.norday.conocimiento.model.dto;

/**
 * Lo que se ve ANTES del match. No lleva {@code contenidoCompleto} a propósito
 * y no debe llevarlo nunca: si el contenido viajara aquí, descartar dejaría de
 * costar nada y el bucle entero perdería el sentido.
 */
public class PildoraPreviewDTO {

    private int pildoraId;
    private String previewCorto;
    private String imagenUrl;

    public int getPildoraId() { return pildoraId; }
    public void setPildoraId(int pildoraId) { this.pildoraId = pildoraId; }

    public String getPreviewCorto() { return previewCorto; }
    public void setPreviewCorto(String previewCorto) { this.previewCorto = previewCorto; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
}

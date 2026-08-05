package com.norday.core.repository;

import com.norday.core.model.CodigoRecuperacion;

public interface ICodigoRecuperacionDAO {

    void save(CodigoRecuperacion codigo);
    CodigoRecuperacion findVigenteByEmailYCodigo(String email, String codigo);
    void update(CodigoRecuperacion codigo);
    void invalidarCodigosDeEmail(String email);

    /**
     * Borrado físico de todos los códigos de un email. Se usa al eliminar la
     * cuenta: invalidarlos no basta, la fila guarda el email en claro.
     */
    void deleteByEmail(String email);
}
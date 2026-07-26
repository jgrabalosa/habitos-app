package com.norday.core.repository;

import com.norday.core.model.CodigoRecuperacion;

public interface ICodigoRecuperacionDAO {

    void save(CodigoRecuperacion codigo);
    CodigoRecuperacion findVigenteByEmailYCodigo(String email, String codigo);
    void update(CodigoRecuperacion codigo);
    void invalidarCodigosDeEmail(String email);
}
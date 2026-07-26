package com.norday.repository;

import com.norday.model.CodigoRecuperacion;

public interface ICodigoRecuperacionDAO {

    void save(CodigoRecuperacion codigo);
    CodigoRecuperacion findVigenteByEmailYCodigo(String email, String codigo);
    void update(CodigoRecuperacion codigo);
    void invalidarCodigosDeEmail(String email);
}
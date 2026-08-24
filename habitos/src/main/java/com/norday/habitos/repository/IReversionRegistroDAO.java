package com.norday.habitos.repository;

import com.norday.habitos.model.ReversionRegistro;

public interface IReversionRegistroDAO {

    void save(ReversionRegistro reversion);

    /** Puede devolver null: los registros anteriores a V9 no tienen instantánea. */
    ReversionRegistro findByRegistro(int registroId);

    void delete(int reversionId);

    /** Borrado en bloque al eliminar la cuenta. Ver LimpiadorHabitos. */
    void deleteByHabito(int habitoId);
}

package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.habitos.repository.IAvisoAppDAO;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IHabitoObjetivoDAO;
import com.norday.habitos.repository.IHitoPagadoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IReversionRegistroDAO;
import com.norday.habitos.repository.IRitmoDAO;
import com.norday.habitos.service.LimpiadorHabitos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

/**
 * Los avisos de la app salen con la cuenta. Con mocks solo se ve que se llama;
 * que el DELETE contra Postgres borra de verdad lo comprueba AvisoAppDAOTest.
 */
@ExtendWith(MockitoExtension.class)
class LimpiadorHabitosAvisosTest {

    @Mock private IHabitoDAO habitoDAO;
    @Mock private IRegistroDAO registroDAO;
    @Mock private IRachaDAO rachaDAO;
    @Mock private IRitmoDAO ritmoDAO;
    @Mock private ICategoriaDAO categoriaDAO;
    @Mock private IReversionRegistroDAO reversionRegistroDAO;
    @Mock private IHitoPagadoDAO hitoPagadoDAO;
    @Mock private IHabitoObjetivoDAO objetivoDAO;
    @Mock private IAvisoAppDAO avisoAppDAO;

    @InjectMocks
    private LimpiadorHabitos limpiador;

    @Test
    void limpiar_borraLosAvisosDeLaAppDelUsuario_aunqueNoTengaHabitos() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(42);

        limpiador.limpiar(usuario);

        verify(avisoAppDAO).deleteByUsuario(42);
    }
}

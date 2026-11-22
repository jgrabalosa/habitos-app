package com.norday.habitos.service;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.service.TextosService;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.dto.CategoriaEntradaDTO;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.repository.IHabitoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CategoriaService {

    /** Tope de categorías propias por usuario. */
    static final int MAX_PROPIAS = 20;
    static final int NOMBRE_MIN = 2;
    static final int NOMBRE_MAX = 30;
    static final int ICONO_MAX = 16;

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private LogrosHabitosService logrosHabitosService;

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private TextosService textosService;

    /**
     * Crea una categoría propia. Validación (400), después el usuario
     * bloqueado (la primera categoría concede un logro y los dos topes se
     * comprueban contando; ver HabitoService.bloquearUsuario), después los
     * conflictos (409). Código y esGlobal los pone el servidor.
     */
    @Transactional
    public Categoria crear(Usuario creador, CategoriaEntradaDTO entrada) {
        String nombre = validar(entrada, creador);
        habitoService.bloquearUsuario(creador);
        if (categoriaDAO.contarPropias(creador) >= MAX_PROPIAS) {
            throw new ConflictoException(textosService.texto("categoria.tope", creador, MAX_PROPIAS));
        }
        comprobarNombreLibre(creador, nombre, 0);

        // El código pertenece al catálogo global: lo pone la siembra, nunca el
        // cliente. Una categoría del usuario no se traduce, se muestra tal cual.
        Categoria categoria = new Categoria(null, nombre, null, null, entrada.getIcono(), false, 0, creador);
        categoriaDAO.save(categoria);

        List<Categoria> categoriasDelUsuario = categoriaDAO.findByCreador(creador);
        logrosHabitosService.evaluarTrasCrearCategoria(creador, categoriasDelUsuario);
        return categoria;
    }

    /** Cambia solo nombre e icono de una categoría propia (quien llama ya comprobó que es suya). */
    @Transactional
    public Categoria actualizar(int id, CategoriaEntradaDTO entrada) {
        Categoria existente = categoriaDAO.findById(id);
        Usuario creador = existente.getCreador();
        String nombre = validar(entrada, creador);
        habitoService.bloquearUsuario(creador);
        comprobarNombreLibre(creador, nombre, id);
        existente.setNombre(nombre);
        existente.setIcono(entrada.getIcono());
        categoriaDAO.update(existente);
        return existente;
    }

    /**
     * Borra una categoría propia. Los hábitos que la usan (también los
     * pausados y los inactivos) pasan a «sin categoría» en la misma
     * transacción, antes del borrado: la clave ajena FK_habito_categoria
     * (habito.tipo_id) no deja borrar una categoría en uso. No se borra ni
     * cambia nada más de los hábitos. El usuario se bloquea primero (ver
     * HabitoService.bloquearUsuario) y el UPDATE bloquea después las filas.
     */
    @Transactional
    public void eliminar(int id) {
        Categoria existente = categoriaDAO.findById(id);
        if (existente == null) {
            return;
        }
        habitoService.bloquearUsuario(existente.getCreador());
        habitoDAO.quitarCategoria(id);
        categoriaDAO.delete(id);
    }

    public Categoria buscarPorId(int id) {
        return categoriaDAO.findById(id);
    }

    public List<Categoria> obtenerPorUsuario(Usuario usuario) {
        return categoriaDAO.findByCreador(usuario);
    }

    public List<Categoria> obtenerTodas(Usuario usuario) {
        return categoriaDAO.findAll(usuario);
    }

    /** Devuelve el nombre recortado; IllegalArgumentException (400) si algo no cumple. */
    private String validar(CategoriaEntradaDTO entrada, Usuario usuario) {
        String nombre = entrada == null || entrada.getNombre() == null ? "" : entrada.getNombre().trim();
        if (nombre.codePointCount(0, nombre.length()) < NOMBRE_MIN
                || nombre.codePointCount(0, nombre.length()) > NOMBRE_MAX) {
            throw new IllegalArgumentException(
                    textosService.texto("categoria.nombre.longitud", usuario, NOMBRE_MIN, NOMBRE_MAX));
        }
        String icono = entrada.getIcono();
        if (icono != null && (icono.isEmpty() || icono.codePointCount(0, icono.length()) > ICONO_MAX)) {
            throw new IllegalArgumentException(
                    textosService.texto("categoria.icono.longitud", usuario, ICONO_MAX));
        }
        return nombre;
    }

    private void comprobarNombreLibre(Usuario creador, String nombre, int excluirId) {
        if (categoriaDAO.existeNombre(creador, nombre, excluirId)) {
            throw new ConflictoException(textosService.texto("categoria.nombre.repetido", creador));
        }
    }
}

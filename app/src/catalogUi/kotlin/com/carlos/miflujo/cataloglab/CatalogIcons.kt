package com.carlos.miflujo.cataloglab

import com.carlos.miflujo.R
import java.text.Normalizer
import java.util.Locale

internal const val DEFAULT_CATALOG_ICON = "category"

internal enum class IconGroup(val label: String) {
    FINANCE("Finanzas"), HOME("Hogar"), SHOPPING("Compras"), TRANSPORT("Transporte"),
    WORK("Trabajo"), HEALTH("Salud"), EDUCATION("Educación y tecnología"),
    LEISURE("Ocio"), OTHER("Otros"),
}

internal data class CatalogIconOption(
    val key: String,
    val name: String,
    val keywords: String,
    val group: IconGroup,
    val resource: Int,
) {
    val searchable = "$name $keywords".normalizedIconSearch()
}

internal val catalogIcons = listOf(
    CatalogIconOption("payments", "Dinero", "billetes efectivo ingreso pago", IconGroup.FINANCE, R.drawable.catalog_symbol_payments),
    CatalogIconOption("account_balance_wallet", "Billetera", "cartera monedero efectivo", IconGroup.FINANCE, R.drawable.catalog_symbol_account_balance_wallet),
    CatalogIconOption("account_balance", "Banco", "cuenta ahorro transferencia", IconGroup.FINANCE, R.drawable.catalog_symbol_account_balance),
    CatalogIconOption("credit_card", "Tarjeta", "crédito débito pago", IconGroup.FINANCE, R.drawable.catalog_symbol_credit_card),
    CatalogIconOption("savings", "Ahorros", "alcancía inversión guardar", IconGroup.FINANCE, R.drawable.catalog_symbol_savings),
    CatalogIconOption("receipt_long", "Recibo", "factura comprobante impuestos", IconGroup.FINANCE, R.drawable.catalog_symbol_receipt_long),
    CatalogIconOption("home", "Casa", "hogar vivienda alquiler", IconGroup.HOME, R.drawable.catalog_symbol_home),
    CatalogIconOption("water", "Agua", "servicio agua potable", IconGroup.HOME, R.drawable.catalog_symbol_water),
    CatalogIconOption("electricity", "Electricidad", "luz energía factura", IconGroup.HOME, R.drawable.catalog_symbol_electricity),
    CatalogIconOption("internet", "Internet", "wifi conexión red", IconGroup.HOME, R.drawable.catalog_symbol_internet),
    CatalogIconOption("chair", "Muebles", "silla sala hogar", IconGroup.HOME, R.drawable.catalog_symbol_chair),
    CatalogIconOption("build", "Herramientas", "reparación mantenimiento", IconGroup.HOME, R.drawable.catalog_symbol_build),
    CatalogIconOption("shopping_cart", "Supermercado", "compras mercado carrito", IconGroup.SHOPPING, R.drawable.catalog_symbol_shopping_cart),
    CatalogIconOption("shopping_bag", "Compras", "bolsa tienda comercio", IconGroup.SHOPPING, R.drawable.catalog_symbol_shopping_bag),
    CatalogIconOption("restaurant", "Restaurante", "comida cena almuerzo", IconGroup.SHOPPING, R.drawable.catalog_symbol_restaurant),
    CatalogIconOption("local_cafe", "Café", "desayuno bebida cafetería", IconGroup.SHOPPING, R.drawable.catalog_symbol_local_cafe),
    CatalogIconOption("local_pizza", "Pizza", "comida rápida alimentación", IconGroup.SHOPPING, R.drawable.catalog_symbol_local_pizza),
    CatalogIconOption("checkroom", "Ropa", "vestuario prendas uniforme", IconGroup.SHOPPING, R.drawable.catalog_symbol_checkroom),
    CatalogIconOption("directions_car", "Automóvil", "carro coche vehículo", IconGroup.TRANSPORT, R.drawable.catalog_symbol_directions_car),
    CatalogIconOption("local_gas_station", "Combustible", "gasolina diésel estación", IconGroup.TRANSPORT, R.drawable.catalog_symbol_local_gas_station),
    CatalogIconOption("directions_bus", "Autobús", "bus transporte público", IconGroup.TRANSPORT, R.drawable.catalog_symbol_directions_bus),
    CatalogIconOption("two_wheeler", "Motocicleta", "moto vehículo", IconGroup.TRANSPORT, R.drawable.catalog_symbol_two_wheeler),
    CatalogIconOption("directions_bike", "Bicicleta", "bici cicla transporte", IconGroup.TRANSPORT, R.drawable.catalog_symbol_directions_bike),
    CatalogIconOption("flight", "Avión", "vuelo viaje transporte", IconGroup.TRANSPORT, R.drawable.catalog_symbol_flight),
    CatalogIconOption("work", "Trabajo", "maletín empleo sueldo", IconGroup.WORK, R.drawable.catalog_symbol_work),
    CatalogIconOption("business_center", "Negocios", "empresa oficina reunión", IconGroup.WORK, R.drawable.catalog_symbol_business_center),
    CatalogIconOption("storefront", "Tienda", "ventas comercio negocio", IconGroup.WORK, R.drawable.catalog_symbol_storefront),
    CatalogIconOption("inventory_2", "Inventario", "mercancía paquetes caja", IconGroup.WORK, R.drawable.catalog_symbol_inventory_2),
    CatalogIconOption("construction", "Construcción", "obra herramienta mantenimiento", IconGroup.WORK, R.drawable.catalog_symbol_construction),
    CatalogIconOption("medical_services", "Salud", "doctor medicina consulta", IconGroup.HEALTH, R.drawable.catalog_symbol_medical_services),
    CatalogIconOption("medication", "Medicamentos", "farmacia pastillas receta", IconGroup.HEALTH, R.drawable.catalog_symbol_medication),
    CatalogIconOption("dentistry", "Dentista", "dental diente odontología", IconGroup.HEALTH, R.drawable.catalog_symbol_dentistry),
    CatalogIconOption("fitness_center", "Gimnasio", "ejercicio deporte pesa", IconGroup.HEALTH, R.drawable.catalog_symbol_fitness_center),
    CatalogIconOption("spa", "Cuidado personal", "bienestar belleza estética", IconGroup.HEALTH, R.drawable.catalog_symbol_spa),
    CatalogIconOption("school", "Educación", "escuela matrícula universidad", IconGroup.EDUCATION, R.drawable.catalog_symbol_school),
    CatalogIconOption("menu_book", "Libros", "lectura estudio biblioteca", IconGroup.EDUCATION, R.drawable.catalog_symbol_menu_book),
    CatalogIconOption("computer", "Computadora", "ordenador tecnología equipo", IconGroup.EDUCATION, R.drawable.catalog_symbol_computer),
    CatalogIconOption("smartphone", "Teléfono", "celular móvil recarga", IconGroup.EDUCATION, R.drawable.catalog_symbol_smartphone),
    CatalogIconOption("software", "Software", "suscripción programa aplicación", IconGroup.EDUCATION, R.drawable.catalog_symbol_software),
    CatalogIconOption("movie", "Cine", "película video entretenimiento", IconGroup.LEISURE, R.drawable.catalog_symbol_movie),
    CatalogIconOption("sports_esports", "Videojuegos", "juego consola ocio", IconGroup.LEISURE, R.drawable.catalog_symbol_sports_esports),
    CatalogIconOption("music_note", "Música", "concierto sonido canción", IconGroup.LEISURE, R.drawable.catalog_symbol_music_note),
    CatalogIconOption("sports_soccer", "Fútbol", "deportes balón", IconGroup.LEISURE, R.drawable.catalog_symbol_sports_soccer),
    CatalogIconOption("beach_access", "Vacaciones", "playa paseo viaje", IconGroup.LEISURE, R.drawable.catalog_symbol_beach_access),
    CatalogIconOption("category", "Predeterminado", "general genérico otros", IconGroup.OTHER, R.drawable.catalog_symbol_category),
    CatalogIconOption("pets", "Mascotas", "perro gato animal", IconGroup.OTHER, R.drawable.catalog_symbol_pets),
    CatalogIconOption("redeem", "Regalo", "cumpleaños obsequio", IconGroup.OTHER, R.drawable.catalog_symbol_redeem),
    CatalogIconOption("volunteer_activism", "Donación", "ayuda caridad solidaridad", IconGroup.OTHER, R.drawable.catalog_symbol_volunteer_activism),
    CatalogIconOption("favorite", "Corazón", "familia cariño afecto", IconGroup.OTHER, R.drawable.catalog_symbol_favorite),
    CatalogIconOption("star", "Estrella", "favorito destacado", IconGroup.OTHER, R.drawable.catalog_symbol_star),
)

private val iconsByKey = catalogIcons.associateBy { it.key }

internal fun resolveCatalogIcon(key: String?): CatalogIconOption {
    val compatibleKey = when (key) {
        "income" -> "payments"
        "maintenance" -> "build"
        else -> key
    }
    return iconsByKey[compatibleKey] ?: iconsByKey.getValue(DEFAULT_CATALOG_ICON)
}

internal fun searchCatalogIcons(query: String, group: IconGroup?): List<CatalogIconOption> {
    val words = query.normalizedIconSearch().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    return catalogIcons.filter { icon ->
        (group == null || icon.group == group) && words.all { it in icon.searchable }
    }
}

private fun String.normalizedIconSearch(): String =
    Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")

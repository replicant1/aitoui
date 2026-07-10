package com.example.aitoui.data

import kotlinx.coroutines.flow.first

/**
 * Produces a human-readable, ASCII-art dump of the current database contents — one bordered table
 * per entity — for debugging. Reads a one-shot snapshot of each table via the repositories.
 *
 * Example table:
 * ```
 * medications (2 rows)
 * +----+-----------+------------------+
 * | id | brandName | activeIngredient |
 * +----+-----------+------------------+
 * | 1  | Panadol   | Paracetamol      |
 * | 2  | Nurofen   | Ibuprofen        |
 * +----+-----------+------------------+
 * ```
 */
object DatabaseDumper {

    suspend fun dump(
        medicationRepository: MedicationRepository,
        formatRepository: DispensableUnitRepository,
        scriptRepository: ScriptRepository,
        dispensationRepository: DispensationRepository,
    ): String {
        val medications = medicationRepository.medications.first()
        val formats = formatRepository.formatsWithMedication.first()
        val scripts = scriptRepository.scripts.first()
        val dispensations = dispensationRepository.dispensations.first()

        return buildString {
            appendLine(
                renderTable(
                    name = "medications",
                    headers = listOf("id", "brandName", "activeIngredient"),
                    rows = medications.map {
                        listOf(it.id.toString(), it.brandName, it.activeIngredient)
                    },
                )
            )
            appendLine()
            appendLine(
                renderTable(
                    name = "dispensable_units",
                    headers = listOf("id", "medicationId", "dosePerTablet", "tabletsPerUnit"),
                    rows = formats.map {
                        listOf(
                            it.formatId.toString(),
                            it.medicationId.toString(),
                            it.dosePerTablet,
                            it.tabletsPerUnit,
                        )
                    },
                )
            )
            appendLine()
            appendLine(
                renderTable(
                    name = "scripts",
                    headers = listOf("id", "dispensableUnitId", "repeats", "validToMillis"),
                    rows = scripts.map {
                        listOf(
                            it.id.toString(),
                            it.dispensableUnitId.toString(),
                            it.repeats.toString(),
                            it.validToMillis.toString(),
                        )
                    },
                )
            )
            appendLine()
            append(
                renderTable(
                    name = "dispensations",
                    headers = listOf("id", "scriptId", "dispensableUnitId", "number", "dispensedAtMillis"),
                    rows = dispensations.map {
                        listOf(
                            it.id.toString(),
                            it.scriptId.toString(),
                            it.dispensableUnitId.toString(),
                            it.number.toString(),
                            it.dispensedAtMillis.toString(),
                        )
                    },
                )
            )
        }
    }

    /** Renders a titled, bordered ASCII table. Column widths fit the widest header/cell in each column. */
    private fun renderTable(name: String, headers: List<String>, rows: List<List<String>>): String {
        val widths = IntArray(headers.size) { col ->
            (rows.map { it[col] } + headers[col]).maxOf { it.length }
        }
        val separator = widths.joinToString(separator = "+", prefix = "+", postfix = "+") {
            "-".repeat(it + 2)
        }
        fun renderRow(cells: List<String>): String =
            cells.mapIndexed { i, cell -> " " + cell.padEnd(widths[i]) + " " }
                .joinToString(separator = "|", prefix = "|", postfix = "|")

        return buildString {
            appendLine("$name (${rows.size} ${if (rows.size == 1) "row" else "rows"})")
            appendLine(separator)
            appendLine(renderRow(headers))
            appendLine(separator)
            rows.forEach { appendLine(renderRow(it)) }
            append(separator)
        }
    }
}

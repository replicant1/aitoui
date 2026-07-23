---
name: android-string-externalisation
description: |
  Pattern for Android string externalisation, internationalisation, i18n, localisation, l12n, string resources. Use this skill whenever you have to extract string literals from code into a reesource file called strings.xml as per Android requirements. Trigger on phrases like "internationalise", "localise", "extract", "externalise", "string literals", "strings.xml", "string resource", "resource", "resources", "string resources", "i18n", "l12n", "language".
---

# Android string resources

## Core principle

We prefer to use Android's string externalisation facility to store strings that are user facing for several reasons
a) Separation of concerns
b) Facilitates translation into multiple languages
c) Standardisation
d) Best practice

---

## Naming

String literals are extracted to a "strings.xml" file that resides in the applications "res/values" directory. The name of the string resources should not just be formed from the literal text being externalised but using this pattern: 
"<short_screen_or_dialog_name>_<role>" so the label of the Save button on the Inventory screen is "inventory_save_button_label".

---

## Arguments

When it comes to externalising dynamically generated strings, use the argument and plurals facilities provided by Android where possible. For instace, a string literal of "Total: ${total}" would become a string resource of "Total: %1$d" and you would apply the "total" variable's value to the sole argument of this using something like "stringResource(R.string.inventory_total_text, total)" - assume the string literal appeared in Jetpack Compose code.

---


## Sorting

The lines in the strings.xml should be alphabetically sorted in increasing order by resource name to make it easy for human editors to find individual resources. Have comments in the strings.xml that separate sections by screen.eg:

<!-- Inventory Screen -->
<string name="inventory_blah_blah_blah">Something</string>

<!-- Medication Screen -->
<string name="medication_blah_blah_blah">Something else </string>

<!-- Unsaved Changes Dialog -->
<string name="unsaved_changes_dialog_title">Unsaved changes</string>

---

## Gotchas

### Gotcha 1

One thing to look out for is this -  apostrophes in strings.xml must be \', not &apos; or &#39;. The entity forms fail the resource compile with a message that names no file or line. For example:

<String name="scan_script_read_form_error">Couldn\'t read the form</string>

### Gotcha 2

Another possible trap: Remember to put double quotes around leading or unusual internal whitespace to make sure it is preserved by AAPT2, for example:

<!-- Quoted so AAPT2 keeps the wides pacing around the separator -->
<string name="bluster-count_summary_pack_label">"Pack %1%d   *   %2$dx%3%d"</string>

<!-- Quoted so AAPT2 keeps the leading space; it separates the dose from the brand name. -->
 <string name="daily_schedule_dose_suffix">" (%1$smg)"</string>

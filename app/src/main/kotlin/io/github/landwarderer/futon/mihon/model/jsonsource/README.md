# JSON Source Data Models

This package contains data models for JSON-based source configurations.

## Models

### Legado Book Source (`LegadoBookSource.kt`)

Models for Legado format book sources, which support novels and manga from various websites.

**Main Model:**
- `LegadoBookSource`: Root configuration containing source metadata and parsing rules

**Rule Models:**
- `SearchRule`: Defines how to parse search results
- `BookInfoRule`: Defines how to parse book/manga details
- `TocRule`: Defines how to parse table of contents (chapter list)
- `ContentRule`: Defines how to parse chapter content

**Example JSON:**
```json
{
  "bookSourceName": "Example Source",
  "bookSourceUrl": "https://example.com",
  "bookSourceType": 0,
  "enabled": true,
  "ruleSearch": {
    "bookList": "div.book-list",
    "name": "h2@text",
    "author": "span.author@text",
    "bookUrl": "a@href"
  },
  "ruleToc": {
    "chapterList": "div.chapter-list li",
    "chapterName": "a@text",
    "chapterUrl": "a@href"
  },
  "ruleContent": {
    "content": "div.content@html"
  }
}
```

### TVBox Configuration (`TVBoxConfig.kt`)

Models for TVBox format video site configurations.

**Main Models:**
- `TVBoxConfig`: Root configuration containing sites and settings
- `TVBoxSite`: Individual video site configuration

**Supporting Models:**
- `TVBoxLive`: Live stream configuration
- `TVBoxParse`: Video parser configuration
- `TVBoxIjk`: IJK player settings
- `TVBoxIjkOption`: Individual IJK option

**Example JSON:**
```json
{
  "sites": [
    {
      "key": "example",
      "name": "Example Video Site",
      "type": 1,
      "api": "https://example.com/api",
      "searchable": 1,
      "quickSearch": 1,
      "filterable": 1
    }
  ]
}
```

## Serialization

All models use `kotlinx.serialization` with the `@Serializable` annotation. They support:

- JSON deserialization from string
- JSON serialization to string
- Lenient parsing (ignores unknown keys)
- Optional fields with default values

## Usage

```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

// Parse Legado source
val legadoSource = json.decodeFromString<LegadoBookSource>(jsonString)

// Parse TVBox config
val tvboxConfig = json.decodeFromString<TVBoxConfig>(jsonString)
```

## Requirements

These models satisfy requirement 4.1 from the design document:
- Define data structures for Legado and TVBox configurations
- Support JSON serialization/deserialization
- Provide default values for optional fields
- Handle nested rule structures

plugins {
	id("com.android.library")
	kotlin("android")
}

android {
	namespace = "eu.kanade.tachiyomi.source"
	compileSdk = 36

	defaultConfig {
		minSdk = 23

		consumerProguardFiles("consumer-rules.pro")
	}

	buildTypes {
		release {
			isMinifyEnabled = false
		}
		// Match app's nightly variant (inherits release configuration)
		create("nightly") {
			initWith(getByName("release"))
			isMinifyEnabled = false
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}
}

dependencies {
	// RxJava 1.x - required by Tachiyomi extensions
	api("io.reactivex:rxjava:1.3.8")
	
	// Jsoup - required for HTML parsing in extensions
	api("org.jsoup:jsoup:1.17.2")
	
	// Kotlin stdlib
	implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
}

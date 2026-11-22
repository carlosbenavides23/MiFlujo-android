"""Verify experimental catalog configuration without Gradle, Android or credentials."""
import json
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
SOURCE_SETS = ROOT / "app/src"
LAB = SOURCE_SETS / "catalogLab"
PREVIEW = SOURCE_SETS / "catalogPreview"
RESOURCE = "default_web_client_id"
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"


class CatalogLabConfigTest(unittest.TestCase):
    def test_empty_web_client_resource_is_exclusive_to_experimental_catalog_ui(self):
        definitions = []
        for file in SOURCE_SETS.glob("*/res/values*/*.xml"):
            root = ET.parse(file).getroot()
            for item in root:
                if item.get("name") == RESOURCE:
                    definitions.append((file.relative_to(SOURCE_SETS).parts[0], (item.text or "").strip()))
        self.assertEqual([("catalogUi", "")], definitions)

    def test_lab_configuration_contains_only_demo_firebase_identity(self):
        config = json.loads((LAB / "google-services.json").read_text(encoding="utf-8"))
        self.assertEqual("demo-miflujo-catalog", config["project_info"]["project_id"])
        self.assertEqual(
            ["com.carlos.miflujo.cataloglab", "com.carlos.miflujo.cataloglab.uitest"],
            [client["client_info"]["android_client_info"]["package_name"] for client in config["client"]],
        )
        for client in config["client"]:
            self.assertEqual([{"current_key": "fake-demo-api-key"}], client["api_key"])
            self.assertFalse(client.get("oauth_client"))

    def test_lab_manifest_removes_default_firebase_and_production_activity(self):
        application = ET.parse(LAB / "AndroidManifest.xml").getroot().find("application")
        removals = {
            node.get(ANDROID + "name")
            for node in application
            if node.get(TOOLS + "node") == "remove"
        }
        self.assertIn("com.google.firebase.provider.FirebaseInitProvider", removals)
        self.assertIn(".MainActivity", removals)

    def test_preview_excludes_network_and_the_production_entry_point(self):
        manifest = ET.parse(PREVIEW / "AndroidManifest.xml").getroot()
        internet = next(
            item for item in manifest.findall("uses-permission")
            if item.get(ANDROID + "name") == "android.permission.INTERNET"
        )
        self.assertEqual("remove", internet.get(TOOLS + "node"))
        application = manifest.find("application")
        removals = {
            node.get(ANDROID + "name") for node in application
            if node.get(TOOLS + "node") == "remove"
        }
        self.assertIn("com.google.firebase.provider.FirebaseInitProvider", removals)
        self.assertIn(".MainActivity", removals)

    def test_preview_configuration_contains_only_demo_firebase_identity(self):
        config = json.loads((PREVIEW / "google-services.json").read_text(encoding="utf-8"))
        self.assertEqual("demo-miflujo-catalog", config["project_info"]["project_id"])
        self.assertEqual(
            ["com.carlos.miflujo.catalogpreview", "com.carlos.miflujo.catalogpreview.uitest"],
            [client["client_info"]["android_client_info"]["package_name"] for client in config["client"]],
        )
        for client in config["client"]:
            self.assertEqual([{"current_key": "fake-demo-api-key"}], client["api_key"])
            self.assertFalse(client.get("oauth_client"))


if __name__ == "__main__":
    unittest.main()

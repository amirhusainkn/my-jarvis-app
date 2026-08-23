import os
import threading
from datetime import datetime
import speech_recognition as sr
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.clock import Clock
from plyer import tts, call, flash

# Android Native Intents for Opening Apps & Contacts
try:
    from jnius import autoclass
    PythonActivity = autoclass('org.kivy.android.PythonActivity')
    Intent = autoclass('android.content.Intent')
    ContactsContract = autoclass('android.provider.ContactsContract')
    ANDROID_ENV = True
except Exception:
    ANDROID_ENV = False


class JarvisUI(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisUI, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 20
        self.spacing = 20

        self.status_label = Label(
            text="Jarvis Background Mein Active Hai...",
            font_size='20sp',
            halign='center',
            valign='middle'
        )
        self.add_widget(self.status_label)

        Clock.schedule_once(self.welcome_speech, 1)

        self.is_listening = True
        self.listener_thread = threading.Thread(target=self.listen_for_wakeword)
        self.listener_thread.daemon = True
        self.listener_thread.start()

    def speak(self, text):
        try:
            tts.speak(text)
        except Exception as e:
            print("TTS Error:", e)

    def welcome_speech(self, dt):
        self.speak("Aadaab... Main background mein haazir hoon. 'Jarvis' keh kar hukum kijiye.")

    def update_status(self, text):
        self.status_label.text = text

    def open_app(self, package_name, app_name):
        """Android app ko package name se open karne ka logic"""
        if ANDROID_ENV:
            try:
                activity = PythonActivity.mActivity
                pm = activity.getPackageManager()
                intent = pm.getLaunchIntentForPackage(package_name)
                if intent:
                    activity.startActivity(intent)
                    self.speak(f"Ji Sir, {app_name} khol raha hoon.")
                else:
                    self.speak(f"Sir, {app_name} aapke phone mein nahi mila.")
            except Exception as e:
                print("App Launch Error:", e)
                self.speak(f"Maaf kijiye Sir, {app_name} kholne mein masla aaya.")
        else:
            self.speak(f"Sir, desktop environment par {app_name} nahi khul sakta.")

    def get_contact_number(self, name_to_find):
        if not ANDROID_ENV:
            return None, name_to_find

        try:
            activity = PythonActivity.mActivity
            resolver = activity.getContentResolver()
            uri = ContactsContract.Contacts.CONTENT_URI
            
            cursor = resolver.query(uri, None, None, None, None)
            if cursor is not None and cursor.getCount() > 0:
                while cursor.moveToNext():
                    id_idx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    name_idx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    has_phone_idx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    contact_id = cursor.getString(id_idx)
                    contact_name = cursor.getString(name_idx)
                    has_phone = cursor.getInt(has_phone_idx)

                    if has_phone > 0 and name_to_find.lower() in contact_name.lower():
                        phone_uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                        p_cursor = resolver.query(
                            phone_uri,
                            None,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            [contact_id],
                            None
                        )
                        if p_cursor is not None and p_cursor.moveToNext():
                            num_idx = p_cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            phone_number = p_cursor.getString(num_idx)
                            p_cursor.close()
                            cursor.close()
                            return phone_number, contact_name
                        if p_cursor is not None:
                            p_cursor.close()
                cursor.close()
        except Exception as e:
            print("Contact Search Error:", e)

        return None, name_to_find

    def listen_for_wakeword(self):
        recognizer = sr.Recognizer()
        with sr.Microphone() as source:
            recognizer.adjust_for_ambient_noise(source, duration=1)
            while self.is_listening:
                try:
                    Clock.schedule_once(lambda dt: self.update_status("Listening for 'Jarvis'..."))
                    audio = recognizer.listen(source, phrase_time_limit=4)

                    try:
                        command = recognizer.recognize_google(audio).lower()
                    except sr.RequestError:
                        command = ""

                    if "jarvis" in command:
                        Clock.schedule_once(lambda dt: self.update_status("Jarvis Active Ho Gaya!"))
                        self.speak("Ji Sir... Main sun raha hoon.")
                        self.listen_for_command(recognizer, source)

                except sr.UnknownValueError:
                    pass
                except Exception as e:
                    print("Listening Error:", e)

    def listen_for_command(self, recognizer, source):
        now = datetime.now()

        try:
            Clock.schedule_once(lambda dt: self.update_status("Aapka hukum sun raha hoon..."))
            audio = recognizer.listen(source, phrase_time_limit=5)

            try:
                user_command = recognizer.recognize_google(audio).lower()
                Clock.schedule_once(lambda dt: self.update_status(f"Aapne kaha: {user_command}"))
            except sr.RequestError:
                user_command = "offline_mode"

            # 1. Torch / Flashlight Control
            if "torch" in user_command or "light" in user_command or "ujala" in user_command or "andhera" in user_command:
                if "off" in user_command or "band" in user_command or "bujha" in user_command:
                    try:
                        flash.off()
                        self.speak("Ji Sir, torch band kar di hai.")
                    except Exception:
                        self.speak("Torch off karne mein masla aaya.")
                else:
                    try:
                        flash.on()
                        self.speak("Ji Sir, torch jala di hai.")
                    except Exception:
                        self.speak("Torch on karne mein masla aaya.")

            # 2. Open Apps (YouTube, WhatsApp, Settings)
            elif "youtube" in user_command:
                self.open_app("com.google.android.youtube", "YouTube")

            elif "whatsapp" in user_command:
                self.open_app("com.whatsapp", "WhatsApp")

            elif "setting" in user_command or "settings" in user_command:
                self.open_app("com.android.settings", "Settings")

            # 3. Call Making & Contact Search
            elif "call" in user_command or "कॉल" in user_command or "phone" in user_command:
                raw_name = user_command.replace("jarvis", "").replace("call", "").replace("कॉल", "").replace("zara", "").replace("laga", "").replace("dijiye", "").replace("karo", "").replace("ko", "").strip()
                
                if raw_name:
                    phone_num, matched_name = self.get_contact_number(raw_name)
                    response_text = f"Yes sir, {raw_name.capitalize()} ko call lagaya ja raha hai."
                    self.speak(response_text)
                    
                    if phone_num:
                        try:
                            call.makecall(tel=phone_num)
                        except Exception as e:
                            print("Call Error:", e)
                    else:
                        Clock.schedule_once(lambda dt: self.speak(f"Maaf kijiye Sir, contacts mein {raw_name} ka number nahi mila."))
                else:
                    self.speak("Kisko call lagana hai Sir, naam batayein?")

            # 4. Time / Waqt
            elif "time" in user_command or "waqt" in user_command or "samay" in user_command:
                current_time = now.strftime("%I:%M %p")
                self.speak(f"Sir, abhi time {current_time} hua hai.")

            # 5. Din (Day)
            elif "din" in user_command or "day" in user_command:
                current_day = now.strftime("%A")
                self.speak(f"Sir, aaj {current_day} hai.")

            # 6. Tareekh (Date)
            elif "date" in user_command or "tareekh" in user_command:
                current_date = now.strftime("%d %B %Y")
                self.speak(f"Sir, aaj ki tareekh {current_date} hai.")

            # 7. Offline Mode
            elif user_command == "offline_mode":
                self.speak("Sir, internet offline hai, lekin main basic commands ke liye ready hoon.")

            else:
                self.speak("Ji Sir, main samajh gaya.")

        except sr.UnknownValueError:
            self.speak("Maaf kijiye Sir, main aapki baat samajh nahi paya.")
        except Exception as e:
            print("Command Processing Error:", e)


class JarvisApp(App):
    def build(self):
        return JarvisUI()


if __name__ == '__main__':
    JarvisApp().run()
                    

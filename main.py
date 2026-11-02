import time
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.utils import platform
from kivy.clock import Clock

if platform == 'android':
    from jnius import autoclass
    PythonActivity = autoclass('org.kivy.android.PythonActivity')
    TextToSpeech = autoclass('android.speech.tts.TextToSpeech')
    Locale = autoclass('java.util.Locale')

class JarvisAssistant(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisAssistant, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 40
        self.spacing = 20

        # स्क्रीन पर दिखने वाला टेक्स्ट
        self.status_label = Label(
            text="[color=00ff66][b]Jarvis Core Online[/b][/color]\n[size=14]सभी सिस्टम सक्रिय हैं[/size]",
            markup=True,
            font_size='18sp',
            halign='center',
            valign='middle'
        )
        self.status_label.bind(size=self.status_label.setter('text_size'))
        self.add_widget(self.status_label)

        # कमांड देने के लिए बटन
        self.listen_btn = Button(
            text="Jarvis Se Baat Karein",
            font_size='18sp',
            size_hint=(1, 0.25),
            background_color=(0.1, 0.5, 0.8, 1)
        )
        self.listen_btn.bind(on_press=self.trigger_action)
        self.add_widget(self.listen_btn)

        # आवाज बोलने वाला इंजन (TTS) सेट करना
        self.tts_engine = None
        if platform == 'android':
            try:
                self.tts_engine = TextToSpeech(PythonActivity.mActivity, None)
                self.tts_engine.setLanguage(Locale.getDefault())
            except Exception as e:
                print("TTS Init Error:", e)

        # ऐप चालू होने के 2 सेकंड बाद टाइम के हिसाब से विश करेगा
        Clock.schedule_once(self.time_based_greeting, 2)

    def speak(self, text):
        self.status_label.text = f"[color=00f0ff][b]{text}[/b][/color]"
        if platform == 'android' and self.tts_engine:
            try:
                self.tts_engine.speak(text, TextToSpeech.QUEUE_FLUSH, None, None)
            except Exception as e:
                print("TTS Speak Error:", e)

    def time_based_greeting(self, dt):
        # मोबाइल का current hour (घंटा) देखना
        current_hour = int(time.strftime("%H"))
        
        # समय के हिसाब से ग्रीटिंग सेट करना
        if 5 <= current_hour < 12:
            time_wish = "Good morning, Sir."
        elif 12 <= current_hour < 17:
            time_wish = "Good afternoon, Sir."
        elif 17 <= current_hour < 21:
            time_wish = "Good evening, Sir."
        else:
            time_wish = "Hello, Sir. Raat ka samay hai."

        greeting = f"{time_wish} Jarvis online hai. Hukum kijiye."
        self.speak(greeting)

    def trigger_action(self, instance):
        # बटन दबाने पर टाइम और स्टेटस बताएगा
        current_time = time.strftime("%I:%M %p")
        response = f"Sabhi systems online hain, Sir. Abhi samay ho raha hai {current_time}"
        self.speak(response)

class JarvisApp(App):
    def build(self):
        return JarvisAssistant()

if __name__ == '__main__':
    JarvisApp().run()
    

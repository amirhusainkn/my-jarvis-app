from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.graphics import Color, Rectangle

# Plyer TTS (Bolne ke liye)
try:
    from plyer import tts
except Exception:
    tts = None

class JarvisAssistant(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisAssistant, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 30
        self.spacing = 20

        # Dark theme background
        with self.canvas.before:
            Color(0.05, 0.05, 0.08, 1)
            self.rect = Rectangle(size=self.size, pos=self.pos)
        self.bind(size=self._update_rect, pos=self._update_rect)

        # Status Label
        self.status_label = Label(
            text="[color=00ff66][b]Jarvis Smart Mode Active Hai...\nNeeche button dabakar test karein[/b][/color]",
            markup=True,
            font_size='18sp',
            halign='center',
            valign='middle'
        )
        self.add_widget(self.status_label)

        # Test karne ke liye button (Yahan aap alag-alag sawal simulate kar sakte hain)
        self.test_btn = Button(
            text="Puchhein: 'Time kya hai?'",
            font_size='18sp',
            size_hint=(1, 0.25),
            background_color=(0.1, 0.5, 0.8, 1)
        )
        self.test_btn.bind(on_press=lambda x: self.handle_user_query("time kya hai"))
        self.add_widget(self.test_btn)

        self.test_btn2 = Button(
            text="Puchhein: 'Aaj ki tarikh kya hai?'",
            font_size='18sp',
            size_hint=(1, 0.25),
            background_color=(0.2, 0.6, 0.4, 1)
        )
        self.test_btn2.bind(on_press=lambda x: self.handle_user_query("tarikh kya hai"))
        self.add_widget(self.test_btn2)

        self.test_btn3 = Button(
            text="Puchhein: 'Aaj kaun sa din hai?'",
            font_size='18sp',
            size_hint=(1, 0.25),
            background_color=(0.7, 0.3, 0.1, 1)
        )
        self.test_btn3.bind(on_press=lambda x: self.handle_user_query("din kaun sa hai"))
        self.add_widget(self.test_btn3)

    def _update_rect(self, instance, value):
        self.rect.pos = instance.pos
        self.rect.size = instance.size

    def speak(self, text):
        if tts:
            try:
                tts.speak(text)
            except Exception as e:
                print("TTS Error:", e)

    def handle_user_query(self, query):
        query = query.lower()
        now = datetime.now()
        
        current_time = now.strftime("%I:%M %p")
        current_date = now.strftime("%d %B %Y")
        current_day = now.strftime("%A")
        
        reply = ""
        
        # Smart Keyword Matching (Aap kuch bhi puchein, yeh samajh kar jawab dega)
        if "time" in query or "samay" in query or "ghadi" in query:
            reply = f"Sir, abhi time {current_time} ho raha hai."
        elif "tarikh" in query or "date" in query or "tareekh" in query:
            reply = f"Sir, aaj ki tarikh {current_date} hai."
        elif "din" in query or "day" in query or "baar" in query:
            reply = f"Sir, aaj {current_day} hai."
        else:
            reply = f"Sir, abhi time {current_time} ho raha hai, tarikh {current_date} hai, aur din {current_day} hai."
        
        self.status_label.text = f"[color=00f0ff][b]Jawab: {reply}[/b][/color]"
        self.speak(reply)


class TestApp(App):
    def build(self):
        return JarvisAssistant()


if __name__ == '__main__':
    TestApp().run()



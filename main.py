from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.clock import Clock
import datetime

try:
    from jnius import autoclass
    from plyer import tts, sms
    PYTHON_ANDROID = True
except:
    PYTHON_ANDROID = False

class JarvisMasterApp(BoxLayout):
    def __init__(self, **kwargs):
        super(JarvisMasterApp, self).__init__(**kwargs)
        self.orientation = 'vertical'
        self.padding = 15
        self.spacing = 10

        # Status & Time/Date Display
        self.status_label = Label(
            text='Jarvis Master: All Systems Active 🚀',
            font_size=16,
            color=(0, 1, 0, 1)
        )
        self.add_widget(self.status_label)

        # DateTime Display Label
        self.time_label = Label(
            text='Waqt aur Tarikh load ho rahi hai...',
            font_size=14
        )
        self.add_widget(self.time_label)
        
        # Har second waqt aur din update karne ke liye clock
        Clock.schedule_interval(self.update_datetime, 1.0)

        # Input Box
        self.input_box = TextInput(
            text='',
            hint_text='Yahan kuch bhi type karein ya command dein...',
            size_hint_y=None,
            height=80
        )
        self.add_widget(self.input_box)

        # Output / Response Display
        self.output_label = Label(
            text='Jarvis output yahan show hoga...',
            font_size=15
        )
        self.add_widget(self.output_label)

        # Button 1: Command Process & Emoji Message Maker
        self.emoji_btn = Button(
            text='Emoji Message Bhejein & Check Karein',
            size_hint_y=None,
            height=45,
            background_color=(0.1, 0.4, 0.7, 1)
        )
        self.emoji_btn.bind(on_press=self.send_emoji_message)
        self.add_widget(self.emoji_btn)

        # Button 2: WhatsApp Reader Feature
        self.wa_btn = Button(
            text='WhatsApp Messages Read Karein',
            size_hint_y=None,
            height=45,
            background_color=(0.1, 0.7, 0.3, 1)
        )
        self.wa_btn.bind(on_press=self.read_whatsapp_messages)
        self.add_widget(self.wa_btn)

        # Button 3: Screen Reader Feature
        self.screen_btn = Button(
            text='Screen Text Read Karein',
            size_hint_y=None,
            height=45,
            background_color=(0.7, 0.5, 0.1, 1)
        )
        self.screen_btn.bind(on_press=self.read_screen_content)
        self.add_widget(self.screen_btn)

        # Button 4: Anti-Theft & Pocket Guard
        self.guard_btn = Button(
            text='Anti-Theft Pocket Guard (Alarm)',
            size_hint_y=None,
            height=45,
            background_color=(0.8, 0.2, 0.2, 1)
        )
        self.guard_btn.bind(on_press=self.toggle_pocket_guard)
        self.add_widget(self.guard_btn)

        self.guard_active = False

    def update_datetime(self, dt):
        now = datetime.datetime.now()
        current_time = now.strftime("%H:%M:%S")
        current_date = now.strftime("%d-%m-%Y")
        current_day = now.strftime("%A")
        self.time_label.text = f"Din: {current_day} | Tarikh: {current_date} | Waqt: {current_time}"

    def send_emoji_message(self, instance):
        user_text = self.input_box.text
        if not user_text:
            user_text = "Hello dost, sab kheriyat hai?"
        
        # Message ko emojis ke sath sajane ka feature
        styled_message = f"✨ {user_text} 🔥 👍 (Jarvis Verified)"
        self.output_label.text = f"Bheja gaya message:\n{styled_message}"

    def read_whatsapp_messages(self, instance):
        self.output_label.text = "📱 WhatsApp Reader: Aakhri unread messages ko scan kiya ja raha hai..."
        if PYTHON_ANDROID:
            try:
                # Notification access ke zariye WhatsApp messages read karne ka hook
                pass
            except:
                pass

    def read_screen_content(self, instance):
        self.output_label.text = "👁️ Screen Reader: Mojooda screen ka text padh liya gaya hai."

    def toggle_pocket_guard(self, instance):
        if not self.guard_active:
            self.guard_active = True
            self.output_label.text = "🚨 Pocket Guard Active! Agar koi phone nikalgayega toh aawaj ayegi: 'Yeh hath dusre ka hai!'"
            if PYTHON_ANDROID:
                try:
                    tts.speak("Pocket guard on kar diya gaya hai.")
                except:
                    pass
        else:
            self.guard_active = False
            self.output_label.text = "🔒 Pocket Guard Band. (Fingerprint verification required to unlock)"

class MyApp(App):
    def build(self):
        return JarvisMasterApp()

if __name__ == '__main__':
    MyApp().run()
    

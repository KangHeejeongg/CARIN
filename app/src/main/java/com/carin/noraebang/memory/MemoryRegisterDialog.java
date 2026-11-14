package com.carin.noraebang.memory;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.carin.noraebang.ui.CarinDialog;
import com.carin.noraebang.ui.CarinIcon;
import com.carin.noraebang.ui.CarinTheme;
import com.carin.noraebang.ui.PillButton;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** 장소 · 제목 · 날짜 · 노래를 입력받아 추억을 저장하는 다이얼로그 */
public final class MemoryRegisterDialog {

    /** 저장 완료 콜백 */
    public interface OnSaved {
        void onSaved(Memory memory);
    }

    private MemoryRegisterDialog() {
    }

    /**
     * 등록 다이얼로그를 표시한다.
     *
     * @param defaultPlace 미리 채울 장소 (현재 목적지 등). null 가능
     */
    public static void show(Activity activity, MemoryStore store,
                            String defaultPlace, OnSaved onSaved) {

        CarinDialog dialog = new CarinDialog(activity, "추억 등록",
                "여행지에서 들었던 노래를 남겨두면, 다음에 그 길을 달릴 때 다시 들려드려요.");

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);

        form.addView(label(activity, "장소", false));
        EditText placeInput = input(activity, "예: 강릉, 부산");
        if (defaultPlace != null && !defaultPlace.trim().isEmpty()) {
            placeInput.setText(defaultPlace.trim());
        }
        form.addView(placeInput, fieldParams(activity));

        form.addView(label(activity, "여행 제목", true));
        EditText titleInput = input(activity, "우리의 강릉 여행");
        form.addView(titleInput, fieldParams(activity));

        form.addView(label(activity, "여행 날짜", true));
        final Calendar selected = Calendar.getInstance();
        final PillButton dateButton = new PillButton(activity, format(selected),
                CarinIcon.Type.CALENDAR, PillButton.Style.GHOST);
        dateButton.setOnClickListener(v -> new DatePickerDialog(
                activity,
                android.R.style.Theme_DeviceDefault_Dialog_Alert,
                (view, year, month, day) -> {
                    selected.set(year, month, day);
                    dateButton.setText(format(selected));
                },
                selected.get(Calendar.YEAR),
                selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH)
        ).show());
        LinearLayout dateRow = new LinearLayout(activity);
        dateRow.addView(dateButton, new LinearLayout.LayoutParams(-2, CarinTheme.dp(activity, 60)));
        form.addView(dateRow);

        // 곡 선택: 스피너 대신 알약 탭 (카탈로그가 작아 한눈에 고를 수 있음)
        form.addView(label(activity, "그날 들었던 노래", true));
        final List<SongCatalog.Song> songs = SongCatalog.all();
        final int[] selectedSong = {0};
        final PillButton[] songButtons = new PillButton[songs.size()];
        LinearLayout songRow = new LinearLayout(activity);
        songRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < songs.size(); i++) {
            final int index = i;
            PillButton chip = new PillButton(activity, songs.get(i).title, CarinIcon.Type.NOTE, PillButton.Style.TAB);
            chip.setTabSelected(i == 0);
            chip.setOnClickListener(v -> {
                selectedSong[0] = index;
                for (int j = 0; j < songButtons.length; j++) songButtons[j].setTabSelected(j == index);
            });
            songButtons[i] = chip;
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, CarinTheme.dp(activity, 60), 1);
            if (i > 0) chipParams.leftMargin = CarinTheme.dp(activity, 10);
            songRow.addView(chip, chipParams);
        }
        form.addView(songRow);

        dialog.setContent(form);
        dialog.addButton("취소", null, PillButton.Style.GHOST, dialog::dismiss);
        dialog.addButton("저장", CarinIcon.Type.PLUS, PillButton.Style.PRIMARY, () -> {
            String place = placeInput.getText().toString().trim();
            String title = titleInput.getText().toString().trim();

            if (place.isEmpty()) {
                placeInput.setError("장소를 입력해 주세요");
                return;
            }
            if (songs.isEmpty()) {
                Toast.makeText(activity, "재생 가능한 곡이 없어 저장할 수 없습니다.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (title.isEmpty()) {
                title = place + " 여행";
            }

            SongCatalog.Song song = songs.get(selectedSong[0]);
            Memory memory = store.create(place, title, format(selected), song.id);

            if (store.add(memory)) {
                Toast.makeText(activity,
                        "추억이 저장되었습니다 · " + place + " / " + song.title,
                        Toast.LENGTH_LONG).show();
                dialog.dismiss();
                onSaved.onSaved(memory);
            } else if (store.isWriteProtected()) {
                // 저장 원문이 손상되어 덮어쓰기를 막은 경우: 입력값은 그대로 두고 이유를 안내
                Toast.makeText(activity, "저장된 추억 데이터를 읽을 수 없어 기존 기록을 보호하기 위해 "
                        + "저장을 막았어요. 기존 데이터는 그대로 보존됩니다.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, "저장에 실패했습니다. 다시 시도해 주세요.",
                        Toast.LENGTH_LONG).show();
            }
        });
        dialog.show();
    }

    private static TextView label(Activity activity, String text, boolean topGap) {
        TextView view = CarinTheme.text(activity, text, 16, CarinTheme.LIGHT_ROSE, CarinTheme.MEDIUM);
        view.setPadding(0, CarinTheme.dp(activity, topGap ? 22 : 0), 0, CarinTheme.dp(activity, 8));
        return view;
    }

    private static EditText input(Activity activity, String hint) {
        EditText edit = new EditText(activity);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setTextSize(20);
        edit.setTextColor(CarinTheme.GLASS_WHITE);
        edit.setHintTextColor(CarinTheme.FAINT);
        int padH = CarinTheme.dp(activity, 20);
        edit.setPadding(padH, 0, padH, 0);
        edit.setBackground(CarinTheme.roundedStroke(activity, 0x14FFFFFF, CarinTheme.STROKE, 1, 18));
        return edit;
    }

    private static LinearLayout.LayoutParams fieldParams(Activity activity) {
        return new LinearLayout.LayoutParams(-1, CarinTheme.dp(activity, 64));
    }

    private static String format(Calendar calendar) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }
}

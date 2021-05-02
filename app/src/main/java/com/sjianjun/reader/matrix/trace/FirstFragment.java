package com.sjianjun.reader.matrix.trace;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.sjianjun.reader.R;


public class FirstFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.matrix_fragment, container, false);
        TextView textView = view.findViewById(R.id.text_view);
        textView.setText("This is the first Fragment!");
        return view;
    }
}
